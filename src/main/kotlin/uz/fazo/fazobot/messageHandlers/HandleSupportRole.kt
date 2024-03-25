package uz.fazo.fazobot.messageHandlers

import org.apache.commons.io.FilenameUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.bots.AbsSender
import uz.fazo.fazobot.*
import java.io.File
import java.io.Serializable
import java.net.URL
import java.util.*

interface HandlerSupportRole {
    fun handle(msg: Message, currentUser: User, absSender: AbsSender)
}

@Service
class HandlerSupportRoleImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueUserRepository: QueueUserRepository,
    private val supporterAndUserMarkRepository: SupporterAndUserMarkRepository,
    private val userMessageRepository: UserMessageRepository,
    @Value("\${telegram-bot.token}") val token: String,
) : HandlerSupportRole {
    override fun handle(msg: Message, currentUser: User, absSender: AbsSender) {
        val chatId = currentUser.chatId.toString()

        when {
            !currentUser.supporterMessageSent || msg.text == "/start" -> {
                absSender.sendMessageMarkSession(
                    chatId,
                    currentUser.interFaceLang,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
                )
                currentUser.supporterMessageSent = true
                userRepository.save(currentUser)
            }

            msg.text == "/language" -> {
                val message =
                    "Interface tilini tanlang \n" +
                            "Выберите язык интерфейса \n" +
                            "Select the interface language"
                absSender.sendMessageMarkUpLang(chatId, message)
            }

            msg.text == "/convesationlang" -> {
                if (currentUser.clientId == null) {
                    val message =
                        "Interface tilini tanlang \n" +
                                "Выберите язык интерфейса \n" +
                                "Select the interface language"
                    absSender.sendMessageMarkUpSupport(currentUser, chatId, message)
                    userRepository.save(currentUser)
                }
            }

            msg.text == FINISH[currentUser.interFaceLang] && currentUser.active && currentUser.clientId != null -> {
                val message = MarkSupproter[currentUser.lang.first()]
                absSender.sendMessageMarkSupporter(currentUser.clientId!!, message!!)

                val clientUser = userRepository.findByChatId(currentUser.clientId!!)
                    ?: throw IllegalArgumentException("user ${currentUser.clientId} not found")
                supporterAndUserMarkRepository.save(SupporterAndUserMark(clientUser, currentUser))

                currentUser.clientId = null
                currentUser.state = SupporterState.FREE
                currentUser.active = true
                userRepository.save(currentUser)

                absSender.sendMessage(chatId, FINISH_INFO[currentUser.interFaceLang]!!)
                val queueUserList = queueUserRepository.findAll()
                var queueUser: QueueUser? = null
                for(queueUserOne in queueUserList){
                    val user = userRepository.findByChatId(queueUserOne.chatId) ?: throw IllegalArgumentException("user not found")
                    if(currentUser.lang.contains(user.lang.first())){
                        queueUser = queueUserOne
                    }
                }

                if (queueUserList.size > 0 && queueUser != null) {
                    currentUser.clientId = queueUser.chatId
                    currentUser.state = SupporterState.BUSY
                    userRepository.save(currentUser)
                    val userMessages = messageRepository.findAllByUserId(queueUser.id!!)
                    val connectedUser = userRepository.findByChatId(currentUser.clientId!!)
                    val userInfo =
                        "${USER_MESSAGE_INFO[currentUser.interFaceLang]}:  ${connectedUser!!.firstName} ${connectedUser.lastName}\n lang: ${connectedUser.lang.first()}"
                    absSender.sendMessage(currentUser.chatId!!, userInfo)
                    for (userMessage in userMessages) {
                        when (userMessage.type) {
                            MessageType.VIDEO_NOTE -> {
                                handleVideoNoteMessage(msg, userMessage, currentUser, "video", absSender)
                            }

                            MessageType.STICKER -> {
                                handleStickerMessage(msg, userMessage, currentUser, "sticker", absSender)
                            }

                            MessageType.DOCUMENT -> {
                                handleDocumentMessage(msg, userMessage, currentUser, "doc", absSender)
                            }

                            MessageType.PHOTO -> {
                                handlePhotoMessage(msg, userMessage, currentUser, "photo", absSender)
                            }

                            MessageType.VOICE -> {
                                handleVoiceMessage(
                                    msg,
                                    userMessage, currentUser, "voice", absSender
                                )
                            }

                            MessageType.VIDEO -> {
                                handleVideoMessage(
                                    msg,
                                    userMessage, currentUser, "video", absSender
                                )
                            }

                            MessageType.MESSAGE -> {
                                absSender.sendMessage(currentUser.chatId!!, userMessage.message!!)
                            }

                            null -> TODO()
                        }
                    }
                    queueUserRepository.deleteById(queueUser.id!!)
                }
            }

            msg.text == ACTIVE[currentUser.interFaceLang] && !currentUser.active -> {
                currentUser.active = true
                val queueUserList = queueUserRepository.findAll()
                var queueUser: QueueUser? = null
                for(queueUserOne in queueUserList){
                    val user = userRepository.findByChatId(queueUserOne.chatId) ?: throw IllegalArgumentException("user not found")
                    if(currentUser.lang.contains(user.lang.first())){
                        queueUser = queueUserOne
                    }
                }
                if (queueUserList.size > 0 && queueUser != null) {
                    currentUser.clientId = queueUser.chatId
                    currentUser.state = SupporterState.BUSY
                    userRepository.save(currentUser)
                    val userMessages = messageRepository.findAllByUserId(queueUser.id!!)
                    val connectedUser = userRepository.findByChatId(currentUser.clientId!!)
                    val userInfo =
                        "${USER_MESSAGE_INFO[currentUser.interFaceLang]}:  ${connectedUser!!.firstName} ${connectedUser.lastName}\nlang: ${connectedUser.lang.first()}"
                    absSender.sendMessage(currentUser.chatId!!, userInfo)
                    for (userMessage in userMessages) {
                        when (userMessage.type) {
                            MessageType.VIDEO_NOTE -> {
                                handleVideoNoteMessage(msg, userMessage, currentUser, "video", absSender)
                            }

                            MessageType.STICKER -> {
                                handleStickerMessage(msg, userMessage, currentUser, "sticker", absSender)
                            }

                            MessageType.DOCUMENT -> {
                                handleDocumentMessage(msg, userMessage, currentUser, "doc", absSender)
                            }

                            MessageType.PHOTO -> {
                                handlePhotoMessage(msg, userMessage, currentUser, "photo", absSender)
                            }

                            MessageType.VOICE -> {
                                handleVoiceMessage(
                                    msg,
                                    userMessage, currentUser, "voice", absSender
                                )
                            }

                            MessageType.VIDEO -> {
                                handleVideoMessage(
                                    msg,
                                    userMessage, currentUser, "video", absSender
                                )
                            }

                            MessageType.MESSAGE -> {
                                absSender.sendMessage(currentUser.chatId, userMessage.message!!)
                            }

                            null -> TODO()
                        }
                    }
                    queueUserRepository.deleteById(queueUser.id!!)
                }
            }

            msg.text == INACTIVE[currentUser.interFaceLang] && currentUser.active -> {
                if (currentUser.clientId != null) {
                    val userClient = userRepository.findByChatId(currentUser.clientId!!)
                    val message = MarkSupproter[userClient?.lang?.first()]
                    absSender.sendMessageMarkSupporter(currentUser.clientId!!, message!!)
                    val clientUser = userRepository.findByChatId(currentUser.clientId!!)
                        ?: throw IllegalArgumentException("user ${currentUser.clientId} not found ")
                    supporterAndUserMarkRepository.save(SupporterAndUserMark(clientUser, currentUser))
                }
                currentUser.clientId = null
                currentUser.state = SupporterState.FREE
                currentUser.active = false
                userRepository.save(currentUser)
                absSender.sendMessage(chatId, INACTIVE_INFO[currentUser.interFaceLang]!!)
            }

            msg.text == LANG.UZ -> {
                currentUser.interFaceLang = UserLang.UZ
                absSender.sendMessage(chatId, "til o'zgardi", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    currentUser.interFaceLang,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
                )
                userRepository.save(currentUser)
            }

            msg.text == LANG.RU -> {
                currentUser.interFaceLang = UserLang.RU
                absSender.sendMessage(chatId, "язык изменен", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    currentUser.interFaceLang,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
                )
                userRepository.save(currentUser)
            }

            msg.text == LANG.ENG -> {
                currentUser.interFaceLang = UserLang.ENG
                absSender.sendMessage(chatId, "language changed", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    currentUser.interFaceLang,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
                )
                userRepository.save(currentUser)
            }

            currentUser.clientId != null -> {
                if (msg.hasText()) {
                    handleTextMessage(msg, currentUser, absSender)
                } else if (msg.hasVideoNote()) {
                    val file = absSender.fileSavetoFolder(msg.videoNote.fileId, "video")
                    val sendVideo = SendVideoNote()
                    sendVideo.chatId = currentUser.clientId!!
                    sendVideo.videoNote = InputFile(file)
                    val responseMessage = absSender.execute(sendVideo)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.VIDEO_NOTE,
                            null,
                            msg.videoNote.fileId
                        )
                    )
                } else if (msg.hasSticker()) {
                    val file = absSender.fileSavetoFolder(msg.sticker.fileId, "stiker")
                    val sticker = SendSticker()
                    sticker.chatId = currentUser.clientId!!
                    sticker.sticker = InputFile(file)
                    val responseMessage = absSender.execute(sticker)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.STICKER,
                            null,
                            msg.voice.fileId
                        )
                    )
                } else if (msg.hasDocument()) {
                    val file = absSender.fileSavetoFolder(msg.document.fileId, msg.document.fileName)
                    val document = SendDocument()
                    document.chatId = currentUser.clientId!!
                    document.document = InputFile(file)
                    val responseMessage = absSender.execute(document)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.DOCUMENT,
                            null,
                            msg.voice.fileId
                        )
                    )
                } else if (msg.hasPhoto()) {
                    val photo = msg.photo.last()
                    val file = absSender.fileSavetoFolder(photo.fileId, "image.png")
                    val sendPhoto = SendPhoto()
                    sendPhoto.chatId = currentUser.clientId!!
                    sendPhoto.photo = InputFile(file)
                    val responseMessage = absSender.execute(sendPhoto)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.PHOTO,
                            null,
                            msg.voice.fileId
                        )
                    )
                } else if (msg.hasVoice()) {
                    val file = absSender.fileSavetoFolder(msg.voice.fileId, "voice.mp3")
                    val sendVoice = SendVoice()
                    sendVoice.voice = InputFile(file)
                    sendVoice.chatId = currentUser.clientId!!
                    val responseMessage = absSender.execute(sendVoice)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.VOICE,
                            null,
                            msg.voice.fileId
                        )
                    )
                } else if (msg.hasVideo()) {
                    val file = absSender.fileSavetoFolder(msg.video.fileId, msg.video.fileName)
                    val sendVideo = SendVideo()
                    sendVideo.video = InputFile(file)
                    sendVideo.chatId = currentUser.clientId!!
                    val responseMessage = absSender.execute(sendVideo)
                    userMessageRepository.save(
                        UserMessage(
                            msg.chatId,
                            msg.messageId,
                            responseMessage.messageId,
                            MessageType.VIDEO,
                            null,
                            msg.video.fileId
                        )
                    )
                }
            }
        }
    }

    private fun AbsSender.sendMessageMarkSession(chatId: String, interfaceLang: UserLang, msg: String) {
        val interfaceLangActive = ACTIVE[interfaceLang]!!
        val interfaceLangInActive = INACTIVE[interfaceLang]!!
        val interfaceLangFinish = FINISH[interfaceLang]!!
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId

        val keyboardMarkUp = ReplyKeyboardMarkup()
        val keyboard: MutableList<KeyboardRow> = mutableListOf()

        val row1 = KeyboardRow()
        val row2 = KeyboardRow()

        val buttonFinish = KeyboardButton()
        buttonFinish.text = interfaceLangActive
        val buttonActive = KeyboardButton()
        buttonActive.text = interfaceLangInActive
        val buttonInActive = KeyboardButton()
        buttonInActive.text = interfaceLangFinish


        row1.add(buttonFinish)
        row1.add(buttonActive)
        row2.add(buttonInActive)
        keyboard.add(row1)
        keyboard.add(row2)

        keyboardMarkUp.keyboard = keyboard
        keyboardMarkUp.resizeKeyboard = true
        keyboardMarkUp.oneTimeKeyboard = true
        sendMessage.replyMarkup = keyboardMarkUp

        this.execute(sendMessage)
    }

    private fun AbsSender.sendMessageMarkSupporter(chatId: String, msg: String) {

        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val td: MutableList<InlineKeyboardButton> = mutableListOf()
        val inlineKeyboardButton1 = InlineKeyboardButton()
        val inlineKeyboardButton2 = InlineKeyboardButton()
        val inlineKeyboardButton3 = InlineKeyboardButton()
        val inlineKeyboardButton4 = InlineKeyboardButton()
        val inlineKeyboardButton5 = InlineKeyboardButton()

        inlineKeyboardButton1.text = "1"
        inlineKeyboardButton1.callbackData = MarkValue.ONE.toString()

        inlineKeyboardButton2.text = "2"
        inlineKeyboardButton2.callbackData = MarkValue.TWO.toString()

        inlineKeyboardButton3.text = "3"
        inlineKeyboardButton3.callbackData = MarkValue.THREE.toString()

        inlineKeyboardButton4.text = "4"
        inlineKeyboardButton4.callbackData = MarkValue.FOUR.toString()

        inlineKeyboardButton5.text = "5"
        inlineKeyboardButton5.callbackData = MarkValue.FIVE.toString()

        td.add(inlineKeyboardButton1)
        td.add(inlineKeyboardButton2)
        td.add(inlineKeyboardButton3)
        td.add(inlineKeyboardButton4)
        td.add(inlineKeyboardButton5)

        val tr: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()
        tr.add(td)
        inlineKeyboardMarkup.keyboard = tr

        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        sendMessage.replyMarkup = inlineKeyboardMarkup
        this.execute(sendMessage)
    }

    private fun AbsSender.sendMessageMarkUpLang(chatId: String, msg: String) {

        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId

        val keyboardMarkUp = ReplyKeyboardMarkup()
        val keyboard: MutableList<KeyboardRow> = mutableListOf()
        val row1 = KeyboardRow()
        val row2 = KeyboardRow()
        val row3 = KeyboardRow()

        val buttonUZ = KeyboardButton()
        buttonUZ.text = LANG.UZ
        val buttonRU = KeyboardButton()
        buttonRU.text = LANG.RU
        val buttonENG = KeyboardButton()
        buttonENG.text = LANG.ENG

        row1.add(buttonUZ)
        row2.add(buttonRU)
        row3.add(buttonENG)

        keyboard.add(row1)
        keyboard.add(row2)
        keyboard.add(row3)

        keyboardMarkUp.keyboard = keyboard
        keyboardMarkUp.resizeKeyboard = true
        keyboardMarkUp.oneTimeKeyboard = true
        sendMessage.replyMarkup = keyboardMarkUp
        execute(sendMessage)
    }

    private fun AbsSender.sendMessage(chatId: String, msg: String, removeKeyboard: Boolean = false): Message {
        val replyKeyboardRemove = ReplyKeyboardRemove()
        replyKeyboardRemove.removeKeyboard = removeKeyboard
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        sendMessage.replyMarkup = replyKeyboardRemove
        return this.execute(sendMessage)
    }

    private fun handleTextMessage(msg: Message, currentUser: User, absSender: AbsSender) {
        val replyMessage = if (msg.isReply) {
            val userMessageByResponseMessageId = userMessageRepository.findByResponseMessageId(
                msg.replyToMessage.messageId
            )
            val userMessageByMessageId = userMessageRepository.findByMessageId(
                msg.replyToMessage.messageId
            )
            val replyMessage = SendMessage()
            replyMessage.chatId = currentUser.clientId!!
            replyMessage.text = msg.text

            if (userMessageByResponseMessageId != null && userMessageByResponseMessageId.responseMessageId == msg.replyToMessage.messageId) {
                replyMessage.replyToMessageId = userMessageByResponseMessageId.messageId
            } else if (
                userMessageByMessageId != null && userMessageByMessageId.messageId == msg.replyToMessage.messageId
            ) {
                replyMessage.replyToMessageId = userMessageByMessageId.responseMessageId
            }
            replyMessage
        } else {
            SendMessage(currentUser.clientId!!, msg.text)
        }
        val responseMessage = absSender.execute(replyMessage)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.MESSAGE,
                msg.text,
            )
        )
    }

    private fun handleVideoNoteMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val sendVideo = SendVideoNote()
        sendVideo.chatId = currentUser.chatId!!
        sendVideo.videoNote = InputFile(file)
        val responseMessage = absSender.execute(sendVideo)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VIDEO_NOTE,
                null,
                userMessage.fileId
            )
        )
    }

    private fun handleStickerMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val sticker = SendSticker()
        sticker.chatId = currentUser.chatId!!
        sticker.sticker = InputFile(file)
        val responseMessage = absSender.execute(sticker)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.STICKER,
                null,
                userMessage.fileId,
            )
        )
    }

    private fun handleDocumentMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val document = SendDocument()
        document.chatId = currentUser.chatId!!
        document.document = InputFile(file)
        val responseMessage = absSender.execute(document)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.DOCUMENT,
                null,
                userMessage.fileId
            )
        )
    }

    private fun handlePhotoMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val sendPhoto = SendPhoto()
        sendPhoto.chatId = currentUser.chatId!!
        sendPhoto.photo = InputFile(file)
        val responseMessage = absSender.execute(sendPhoto)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.PHOTO,
                null,
                userMessage.fileId
            )
        )
    }

    private fun handleVoiceMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val sendVoice = SendVoice()
        sendVoice.chatId = currentUser.chatId!!
        sendVoice.voice = InputFile(file)
        val responseMessage = absSender.execute(sendVoice)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VOICE,
                null,
                userMessage.fileId
            )
        )
    }

    private fun handleVideoMessage(
        msg: Message,
        userMessage: QueueMessage,
        currentUser: User,
        fileName: String,
        absSender: AbsSender
    ) {
        val file = absSender.fileSavetoFolder(userMessage.fileId!!, fileName)
        val sendVideo = SendVideo()
        sendVideo.video = InputFile(file)
        sendVideo.chatId = currentUser.chatId!!
        val responseMessage = absSender.execute(sendVideo)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VIDEO,
                null,
                userMessage.fileId
            )
        )
    }

    private fun AbsSender.fileSavetoFolder(fileId: String, fileName: String): File {
        val file = GetFile(fileId)
        val tgFile = this.execute(file)
        val fileUrl = tgFile.getFileUrl(token)
        val url = URL(fileUrl)
        val inputStream = url.openStream()

        val originalFileExtension = FilenameUtils.getExtension(fileName)

        val randomNumber = Random().nextInt(100000)
        val newFileName = "${fileName.substringBeforeLast(".")}_$randomNumber.$originalFileExtension"
        val tempFile =
            java.io.File.createTempFile(newFileName.substringBeforeLast("."), ".${newFileName.substringAfterLast(".")}")
        tempFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        return tempFile

    }


    private fun AbsSender.sendMessageMarkUpSupport(user: User, chatId: String, msg: String) {
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val td: MutableList<InlineKeyboardButton> = mutableListOf()
        val tdSubmit: MutableList<InlineKeyboardButton> = mutableListOf()
        val inlineKeyboardButtonUZ = InlineKeyboardButton()
        val inlineKeyboardButtonRU = InlineKeyboardButton()
        val inlineKeyboardButtonENG = InlineKeyboardButton()
        val inlineKeyboardButtonDONE = InlineKeyboardButton()

        inlineKeyboardButtonUZ.text =
            if (user.lang.contains(UserLang.UZ)) "✅ \uD83C\uDDFA\uD83C\uDDFF UZ" else "\uD83C\uDDFA\uD83C\uDDFF UZ"
        inlineKeyboardButtonUZ.callbackData = LANG.UZ

        inlineKeyboardButtonRU.text =
            if (user.lang.contains(UserLang.RU)) "✅ \uD83C\uDDF7\uD83C\uDDFA RU" else "\uD83C\uDDF7\uD83C\uDDFA RU"
        inlineKeyboardButtonRU.callbackData = LANG.RU

        inlineKeyboardButtonENG.text =
            if (user.lang.contains(UserLang.ENG)) "✅ \uD83C\uDDFA\uD83C\uDDF8 ENG" else "\uD83C\uDDFA\uD83C\uDDF8 ENG"
        inlineKeyboardButtonENG.callbackData = LANG.ENG

        inlineKeyboardButtonDONE.text = "DONE"
        inlineKeyboardButtonDONE.callbackData = "DONE"

        td.add(inlineKeyboardButtonUZ)
        td.add(inlineKeyboardButtonRU)
        td.add(inlineKeyboardButtonENG)

        tdSubmit.add(inlineKeyboardButtonDONE)

        val tr: MutableList<MutableList<InlineKeyboardButton>> = mutableListOf()

        tr.add(td)
        tr.add(tdSubmit)

        inlineKeyboardMarkup.keyboard = tr
        sendMessage.replyMarkup = inlineKeyboardMarkup
        execute(sendMessage)
    }

}