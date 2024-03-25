package uz.fazo.fazobot

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
import java.io.File
import java.net.URL
import java.util.*


interface MessageHandlerTest {
    fun handle(message: Message, absSender: AbsSender)
}


@Service
class MessageHandlerTestImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueUserRepository: QueueUserRepository,
    private val supporterAndUserMarkRepository: SupporterAndUserMarkRepository,
    @Value("\${telegram-bot.token}") val token: String,

    ) : MessageHandlerTest {

    override fun handle(msg: Message, absSender: AbsSender) {

        val chatId = msg.chatId.toString()
        val currentUser = findOrCreateUser(chatId)

        if (currentUser.role == ROLE.SUPPORTER && (!currentUser.supporterMessageSent || msg.text == "/start")) {
            absSender.sendMessageMarkSession(
                chatId,
                currentUser.interFaceLang,
                "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
            )
            currentUser.supporterMessageSent = true
            userRepository.save(currentUser)
        }

        if (msg.text == "/language" && currentUser.role == ROLE.SUPPORTER) {
            val message =
                "Interface tilini tanlang \n" +
                        "Выберите язык интерфейса \n" +
                        "Select the interface language"
            absSender.sendMessageMarkUpLang(chatId, message)
        } else if (msg.text == FINISH[currentUser.interFaceLang] && currentUser.role == ROLE.SUPPORTER && currentUser.active && currentUser.clientId != null) {
            val message = MarkSupproter[currentUser.lang.first()]
            absSender.sendMessageMarkSupporter(currentUser.clientId!!, message!!)

            val clientUser = userRepository.findByChatId(currentUser.clientId!!)
                ?: throw IllegalArgumentException("user ${currentUser.clientId} not found")
            supporterAndUserMarkRepository.save(SupporterAndUserMark(clientUser, currentUser))

            currentUser.clientId = null
            currentUser.state = SupporterState.FREE
            currentUser.active = true

            userRepository.save(currentUser)

            absSender.sendMessage(chatId, "session finished")
            val queueUserList = queueUserRepository.findAll()

            if (queueUserList.size > 0) {
                val queueUser = queueUserList.first()
                currentUser.clientId = queueUser.chatId
                currentUser.state = SupporterState.BUSY
                userRepository.save(currentUser)
                val userMessages = messageRepository.findAllByUserId(queueUser.id!!)
                val connectedUser = userRepository.findByClientId(currentUser.clientId!!)
                val userInfo =
                    "Message from new user:  ${connectedUser!!.firstName} ${connectedUser.lastName}\nlang: ${connectedUser.lang}"
                absSender.sendMessage(currentUser.chatId!!, userInfo)
                for (userMessage in userMessages) {
                    if (userMessage.type ==  MessageType.VIDEO_NOTE) {
                        val file = absSender.fileSavetoFolder(userMessage.fileId!!, "video")
                        val sendVideo = SendVideoNote()
                        sendVideo.chatId = currentUser.clientId!!
                        sendVideo.videoNote = InputFile(file)
                        absSender.execute(sendVideo)
                    } else if (userMessage.type ==  MessageType.STICKER) {
                        val file = absSender.fileSavetoFolder(userMessage.fileId!!, "stiker")
                        val sticker = SendSticker()
                        sticker.chatId = currentUser.chatId!!
                        sticker.sticker = InputFile(file)
                        absSender.execute(sticker)
                    } else if (userMessage.type ==  MessageType.DOCUMENT) {
                        val file = absSender.fileSavetoFolder(userMessage.fileId!!, "doc")
                        val document = SendDocument()
                        document.chatId = currentUser.chatId!!
                        document.document = InputFile(file)
                        absSender.execute(document)
                    } else if (userMessage.type ==  MessageType.PHOTO) {
                        val photo = msg.photo.last()
                        val tempPhoto = absSender.fileSavetoFolder(userMessage.fileId!!, "image")
                        val sendPhoto = SendPhoto()
                        sendPhoto.chatId = currentUser.chatId!!
                        sendPhoto.photo = InputFile(tempPhoto)
                        absSender.execute(sendPhoto)

                    } else if (userMessage.type ==  MessageType.VOICE) {
                        val voice = absSender.fileSavetoFolder(userMessage.fileId!!, "voice")
                        val sendVoice = SendVoice()
                        sendVoice.voice = InputFile(voice)
                        sendVoice.chatId = currentUser.chatId!!
                        absSender.execute(sendVoice)
                    } else if (userMessage.type ==  MessageType.VIDEO) {
                        val video = absSender.fileSavetoFolder(userMessage.fileId!!, "video")
                        val sendVideo = SendVideo()
                        sendVideo.video = InputFile(video)
                        sendVideo.chatId = currentUser.clientId!!
                        absSender.execute(sendVideo)
                    }else if( userMessage.type == MessageType.MESSAGE) {
                        absSender.sendMessage(currentUser.chatId, userMessage.message!!)
                    }
                }
                queueUserRepository.deleteById(queueUser.id)
            }

        } else if (msg.text == ACTIVE[currentUser.interFaceLang] && currentUser.role == ROLE.SUPPORTER && !currentUser.active) {
            currentUser.active = true
            val queueUserList = queueUserRepository.findAll()

            if (queueUserList.size > 0) {
                val queueUser = queueUserList.first()
                currentUser.clientId = queueUser.chatId
                currentUser.state = SupporterState.BUSY

                userRepository.save(currentUser)
                val userMessages = messageRepository.findAllByUserId(queueUser.id!!)
                val connectedUser = userRepository.findByClientId(currentUser.clientId!!)
                val userInfo =
                    "Message from new user:  ${connectedUser!!.firstName} ${connectedUser.lastName}\nlang: ${connectedUser.lang}"
                absSender.sendMessage(currentUser.chatId!!, userInfo)
               /* for (userMessage in userMessages) {
                    absSender.sendMessage(currentUser.chatId, userMessage.message)
                }*/
                queueUserRepository.deleteById(queueUser.id)
            }
        } else if (msg.text == INACTIVE[currentUser.interFaceLang] && currentUser.role == ROLE.SUPPORTER && currentUser.active) {
            val message = MarkSupproter[currentUser.lang.first()]
            absSender.sendMessageMarkSupporter(currentUser.clientId!!, message!!)

            val clientUser = userRepository.findByChatId(currentUser.clientId!!)
                ?: throw IllegalArgumentException("user ${currentUser.clientId} not found ")
            supporterAndUserMarkRepository.save(SupporterAndUserMark(clientUser, currentUser))
            currentUser.clientId = null
            currentUser.state = SupporterState.FREE
            currentUser.active = false
            userRepository.save(currentUser)
            absSender.sendMessage(chatId, "session finished and you are inactive now")
        } else if (msg.text == LANG.UZ) {
            if (currentUser.registerState == RegisterState.SELECT_LANG) {
                currentUser.lang = mutableSetOf(UserLang.UZ)
                val message = EnterFistName[UserLang.UZ]
                absSender.sendMessage(chatId, message!!, true)
                currentUser.registerState = RegisterState.ENTER_LAST_NAME
                userRepository.save(currentUser)
            } else {
                currentUser.interFaceLang = UserLang.UZ
                absSender.sendMessage(chatId, "til o'zgardi", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    UserLang.UZ,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"

                )
                userRepository.save(currentUser)
            }
        } else if (msg.text == LANG.RU) {
            if (currentUser.registerState == RegisterState.SELECT_LANG) {
                currentUser.lang = mutableSetOf(UserLang.RU)
                val message = EnterFistName[UserLang.RU]
                absSender.sendMessage(chatId, message!!, true)
                currentUser.registerState = RegisterState.ENTER_LAST_NAME
                userRepository.save(currentUser)
            } else {
                currentUser.interFaceLang = UserLang.RU
                absSender.sendMessage(chatId, "язык изменен", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    UserLang.RU,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"
                )
                userRepository.save(currentUser)
            }

        } else if (msg.text == LANG.ENG) {
            if (currentUser.registerState == RegisterState.SELECT_LANG) {
                currentUser.lang = mutableSetOf(UserLang.ENG)
                val message = EnterFistName[UserLang.ENG]
                absSender.sendMessage(chatId, message!!, true)
                currentUser.registerState = RegisterState.ENTER_LAST_NAME
                userRepository.save(currentUser)
            } else {
                currentUser.interFaceLang = UserLang.ENG
                absSender.sendMessage(chatId, "language changed", true)
                absSender.sendMessageMarkSession(
                    chatId,
                    UserLang.ENG,
                    "${SUPPORT_CONDITION[currentUser.interFaceLang]} ${if (currentUser.active) ACTIVE[currentUser.interFaceLang] else INACTIVE[currentUser.interFaceLang]}"

                )
                userRepository.save(currentUser)
            }

        } else if (currentUser.role == ROLE.USER) {
            if (msg.text == "/start"
                && currentUser.registerState != RegisterState.FINISH
            ) {
                val message = "Assalomu alaykum \n" +
                        "Muloqot tilini tanlang \n" +
                        "Select language \n" +
                        "Выберите язык"
                absSender.sendMessageMarkUpLang(chatId, message)
                currentUser.registerState = RegisterState.SELECT_LANG
                userRepository.save(currentUser)
            } else if (RegisterState.ENTER_FIRST_NAME == currentUser.registerState) {
                val message = EnterFistName[currentUser.lang.first()]
                absSender.sendMessage(chatId, message!!, true)
                currentUser.registerState = RegisterState.ENTER_LAST_NAME
                userRepository.save(currentUser)
            } else if (RegisterState.ENTER_LAST_NAME == currentUser.registerState) {
                currentUser.firstName = msg.text
                val message = EnterLastName[currentUser.lang.first()]
                currentUser.registerState = RegisterState.REGISTERED
                userRepository.save(currentUser)
                absSender.sendMessage(chatId, message!!)
            } else if (RegisterState.REGISTERED == currentUser.registerState) {
                currentUser.lastName = msg.text
                val message = RegisterFinish[currentUser.lang.first()]
                currentUser.registerState = RegisterState.FINISH
                userRepository.save(currentUser)
                absSender.sendMessage(chatId, message!!)
            }
            // user message shu yerga keladi
            else {
                val connectedSupporter = userRepository.findByClientId(currentUser.chatId!!)
                if (connectedSupporter != null) {
                    if (msg.hasText()) {
                        absSender.sendMessage(connectedSupporter.chatId!!, msg.text)
                    } else if (msg.hasDocument()) {
                        val file = absSender.fileSavetoFolder(msg.document.fileId, msg.document.fileName)
                        val document = SendDocument()
                        document.chatId = connectedSupporter.chatId!!
                        document.document = InputFile(file)
                        absSender.execute(document)
                    } else if (msg.hasPhoto()) {
                        val photo = msg.photo.last()
                        val tempPhoto = absSender.fileSavetoFolder(photo.fileId, "image.png")
                        val sendPhoto = SendPhoto()
                        sendPhoto.chatId = connectedSupporter.chatId!!
                        sendPhoto.photo = InputFile(tempPhoto)
                        absSender.execute(sendPhoto)

                    } else if (msg.hasVoice()) {
                        val voice = absSender.fileSavetoFolder(msg.voice.fileId, "image.png")
                        val sendVoice = SendVoice()
                        sendVoice.voice = InputFile(voice)
                        sendVoice.chatId = connectedSupporter.chatId!!
                        absSender.execute(sendVoice)
                    } else if (msg.hasVideo()) {
                        val video = absSender.fileSavetoFolder(msg.video.fileId, msg.video.fileName ?: "video.mp4")
                        val sendVideo = SendVideo()
                        sendVideo.video = InputFile(video)
                        sendVideo.chatId = currentUser.clientId!!
                        absSender.execute(sendVideo)
                    }

                } else {
                    val supporterList = userRepository.findAllByRole(ROLE.SUPPORTER) ?: emptyList()
                    var isSupportExist = false
                    for (supporter in supporterList) {
                        if (supporter.lang == currentUser.lang && supporter.state == SupporterState.FREE && supporter.active) {
                            isSupportExist = true
                            supporter.clientId = chatId
                            supporter.state = SupporterState.BUSY
                            userRepository.save(supporter)
                            val userInfo =
                                "Message from new user: ${currentUser.firstName} ${currentUser.lastName} \nlang: ${currentUser.lang}"
                            absSender.sendMessage(supporter.chatId!!, userInfo)
                            absSender.sendMessage(supporter.chatId!!, msg.text)
                            break
                        }
                    }

                    if (!isSupportExist) {
                        val queueUserExisting = queueUserRepository.findByChatId(currentUser.chatId!!)

                        if (queueUserExisting == null) {
                            val message = QueueMessage(msg.text)
                            val queueUser = QueueUser(currentUser.chatId, mutableListOf(message))
                            message.queueUser = queueUser
                            queueUserRepository.save(queueUser)
                        } else {
/*
                            messageRepository.save(QueueMessage(msg.text, queueUserExisting))
*/
                        }
                    }
                }
            }

        } else if (currentUser.role == ROLE.SUPPORTER) {
            if (currentUser.clientId != null) {
                sendMessageUser(currentUser,msg, absSender)
            }
        }
    }

    private fun findOrCreateUser(chatId: String): User =
        userRepository.findByChatId(chatId) ?: userRepository.save(User(chatId = chatId))

    private fun AbsSender.sendMessageMarkSupporter(chatId: String, msg: String) {
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
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
        sendMessage.replyMarkup = inlineKeyboardMarkup
        this.execute(sendMessage)
    }

    private fun AbsSender.sendMessage(chatId: String, msg: String, removeKeyboard: Boolean = false) {
        val replyKeyboardRemove = ReplyKeyboardRemove()
        replyKeyboardRemove.removeKeyboard = removeKeyboard
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        sendMessage.replyMarkup = replyKeyboardRemove
        this.execute(sendMessage)
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

    private fun sendMessageUser(currentUser: User, msg: Message, absSender: AbsSender){
        if (msg.hasText()) {
            absSender.sendMessage(currentUser.clientId!!, msg.text)
        } else if (msg.hasDocument()) {
            val file = absSender.fileSavetoFolder(msg.document.fileId, msg.document.fileName)
            val document = SendDocument()
            document.chatId = currentUser.clientId!!
            document.document = InputFile(file)
            absSender.execute(document)
        } else if (msg.hasPhoto()) {
            val photo = msg.photo.last()
            val tempPhoto = absSender.fileSavetoFolder(photo.fileId, "image.png")
            val sendPhoto = SendPhoto()
            sendPhoto.chatId = currentUser.clientId!!
            sendPhoto.photo = InputFile(tempPhoto)
            absSender.execute(sendPhoto)

        } else if (msg.hasVoice()) {
            val voice = absSender.fileSavetoFolder(msg.voice.fileId, "voice.mp3")
            val sendVoice = SendVoice()
            sendVoice.voice = InputFile(voice)
            sendVoice.chatId = currentUser.clientId!!
            absSender.execute(sendVoice)
        } else if (msg.hasVideo()) {
            val video = absSender.fileSavetoFolder(msg.video.fileId, msg.video.fileName ?: "video.mp4")
            val sendVideo = SendVideo()
            sendVideo.video = InputFile(video)
            sendVideo.chatId = currentUser.clientId!!
            absSender.execute(sendVideo)
        }
    }

}


