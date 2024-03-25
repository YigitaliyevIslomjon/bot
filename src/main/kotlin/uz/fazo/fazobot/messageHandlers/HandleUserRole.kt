package uz.fazo.fazobot.messageHandlers

import org.apache.commons.io.FilenameUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.methods.send.*
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow
import org.telegram.telegrambots.meta.bots.AbsSender
import uz.fazo.fazobot.*
import java.io.File
import java.net.URL
import java.util.*

interface HandlerUserRole {
    fun handle(msg: Message, currentUser: User, absSender: AbsSender)
}

@Service
class HandlerUserRoleImpl(
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val queueUserRepository: QueueUserRepository,
    private val userMessageRepository: UserMessageRepository,
    @Value("\${telegram-bot.token}") val token: String,
) : HandlerUserRole {

    override fun handle(msg: Message, currentUser: User, absSender: AbsSender) {

        val chatId = currentUser.chatId.toString()
        when {
            msg.text == "/start" && currentUser.registerState != RegisterState.FINISH -> {
                val message = "Assalomu alaykum \n" +
                        "Muloqot tilini tanlang \n" +
                        "Select language \n" +
                        "Выберите язык"
                absSender.sendMessageMarkUpLang(chatId, message)
                currentUser.registerState = RegisterState.SELECT_LANG
                userRepository.save(currentUser)
            }

            msg.text == "/convesationlang" && currentUser.registerState == RegisterState.FINISH -> {
                val connectedSupporter = userRepository.findByClientId(currentUser.chatId!!)
                if (connectedSupporter == null) {
                    val message =
                        "Muloqot tilini tanlang \n" +
                                "Выберите язык интерфейса \n" +
                                "Select the interface language"
                    absSender.sendMessageMarkUpLang(chatId, message)
                    currentUser.registerState = RegisterState.SELECT_INTERFACE_LANG
                    userRepository.save(currentUser)
                }
            }

            RegisterState.ENTER_FIRST_NAME == currentUser.registerState -> {
                val message = EnterFistName[currentUser.lang.first()]
                absSender.sendMessage(chatId, message!!, true)
                currentUser.registerState = RegisterState.ENTER_LAST_NAME
                userRepository.save(currentUser)
            }

            RegisterState.ENTER_LAST_NAME == currentUser.registerState -> {
                currentUser.firstName = msg.text
                val message = EnterLastName[currentUser.lang.first()]
                currentUser.registerState = RegisterState.REGISTERED
                userRepository.save(currentUser)
                absSender.sendMessage(chatId, message!!)
            }

            RegisterState.REGISTERED == currentUser.registerState -> {
                currentUser.lastName = msg.text
                val message = RegisterFinish[currentUser.lang.first()]
                currentUser.registerState = RegisterState.FINISH
                userRepository.save(currentUser)
                absSender.sendMessage(chatId, message!!)
            }

            msg.text == LANG.UZ && (currentUser.registerState == RegisterState.SELECT_LANG || currentUser.registerState == RegisterState.SELECT_INTERFACE_LANG) -> {
                currentUser.lang = mutableSetOf(UserLang.UZ)
                if (currentUser.registerState == RegisterState.SELECT_LANG) {
                    val message = EnterFistName[UserLang.UZ]
                    absSender.sendMessage(chatId, message!!, true)
                    currentUser.registerState = RegisterState.ENTER_LAST_NAME
                } else {
                    absSender.sendMessage(chatId, "til o'zgardi", true)
                    currentUser.registerState = RegisterState.FINISH
                }
                userRepository.save(currentUser)
            }

            msg.text == LANG.RU && (currentUser.registerState == RegisterState.SELECT_LANG || currentUser.registerState == RegisterState.SELECT_INTERFACE_LANG) -> {
                currentUser.lang = mutableSetOf(UserLang.RU)
                if (currentUser.registerState == RegisterState.SELECT_LANG) {
                    val message = EnterFistName[UserLang.RU]
                    absSender.sendMessage(chatId, message!!, true)
                    currentUser.registerState = RegisterState.ENTER_LAST_NAME
                } else {
                    absSender.sendMessage(chatId, "язык изменен", true)
                    currentUser.registerState = RegisterState.FINISH
                }
                userRepository.save(currentUser)
            }

            msg.text == LANG.ENG && (currentUser.registerState == RegisterState.SELECT_LANG || currentUser.registerState == RegisterState.SELECT_INTERFACE_LANG) -> {
                currentUser.lang = mutableSetOf(UserLang.ENG)
                if (currentUser.registerState == RegisterState.SELECT_LANG) {
                    val message = EnterFistName[UserLang.ENG]
                    absSender.sendMessage(chatId, message!!, true)
                    currentUser.registerState = RegisterState.ENTER_LAST_NAME
                } else {
                    absSender.sendMessage(chatId, "language changed", true)
                    currentUser.registerState = RegisterState.FINISH
                }
                userRepository.save(currentUser)
            }

            else -> {
                val connectedSupporter = userRepository.findByClientId(currentUser.chatId!!)
                if (connectedSupporter != null) {
                    handleMessageForConnectedSupporter(msg, connectedSupporter, absSender)
                } else {
                    handleQueueUser(msg, currentUser, absSender)
                }
            }
        }
    }

    private fun handleMessageForConnectedSupporter(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        if (msg.hasText()) {
            handleTextMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasVideoNote()) {
            handleVideoNoteMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasSticker()) {
            handleStickerMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasDocument()) {
            handleDocumentMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasPhoto()) {
            handlePhotoMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasVoice()) {
            handleVoiceMessage(msg, connectedSupporter, absSender)
        } else if (msg.hasVideo()) {
            handleVideoMessage(msg, connectedSupporter, absSender)
        }
    }

    private fun handleQueueUser(msg: Message, currentUser: User, absSender: AbsSender) {
        val supporterList = userRepository.findAllByRole(ROLE.SUPPORTER) ?: emptyList()
        var isSupportExist = false
        for (supporter in supporterList) {
            if (supporter.lang.contains(currentUser.lang.first()) && supporter.state == SupporterState.FREE && supporter.active) {
                isSupportExist = true
                supporter.clientId = currentUser.chatId
                supporter.state = SupporterState.BUSY
                userRepository.save(supporter)
                val userInfo =
                    "${USER_MESSAGE_INFO[supporter.interFaceLang]} : ${currentUser.firstName} ${currentUser.lastName} \nlang: ${currentUser.lang.first()}"
                val responseMessageInfo = absSender.sendMessage(supporter.chatId!!, userInfo)
                val responseMessage = absSender.sendMessage(supporter.chatId!!, msg.text)
                userMessageRepository.save(
                    UserMessage(
                        msg.chatId,
                        msg.messageId,
                        responseMessageInfo.messageId,
                        MessageType.MESSAGE,
                        msg.text,
                    )
                )
                userMessageRepository.save(
                    UserMessage(
                        msg.chatId,
                        msg.messageId,
                        responseMessage.messageId,
                        MessageType.MESSAGE,
                        msg.text,
                    )
                )
                break
            }
        }
        if (!isSupportExist) {
            val queueUserExisting = queueUserRepository.findByChatId(currentUser.chatId!!)
            if (queueUserExisting == null) {
                lateinit var message: QueueMessage
                if (msg.hasText()) {
                    message = QueueMessage(msg.text, null, MessageType.MESSAGE)
                } else if (msg.hasVideoNote()) {
                    message = QueueMessage(msg.text, msg.videoNote.fileId, MessageType.VIDEO)
                } else if (msg.hasSticker()) {
                    message = QueueMessage(msg.text, msg.sticker.fileId, MessageType.STICKER)
                } else if (msg.hasDocument()) {
                    message = QueueMessage(msg.text, msg.document.fileId, MessageType.DOCUMENT)
                } else if (msg.hasPhoto()) {
                    message = QueueMessage(msg.text, msg.photo.last.fileId, MessageType.PHOTO)
                } else if (msg.hasVoice()) {
                    message = QueueMessage(msg.text, msg.voice.fileId, MessageType.VOICE)
                } else if (msg.hasVideo()) {
                    message = QueueMessage(msg.text, msg.video.fileId, MessageType.VIDEO)
                }

                val queueUser = QueueUser(currentUser.chatId, mutableListOf(message))
                message.queueUser = queueUser
                queueUserRepository.save(queueUser)

            } else {
                lateinit var message: QueueMessage
                if (msg.hasText()) {
                    message = QueueMessage(msg.text, null, MessageType.MESSAGE, queueUserExisting)
                } else if (msg.hasVideoNote()) {
                    message =
                        QueueMessage(msg.text, msg.videoNote.fileId, MessageType.VIDEO, queueUserExisting)
                } else if (msg.hasSticker()) {
                    message =
                        QueueMessage(msg.text, msg.sticker.fileId, MessageType.STICKER, queueUserExisting)
                } else if (msg.hasDocument()) {
                    message =
                        QueueMessage(msg.text, msg.document.fileId, MessageType.DOCUMENT, queueUserExisting)
                } else if (msg.hasPhoto()) {
                    message =
                        QueueMessage(msg.text, msg.photo.last.fileId, MessageType.PHOTO, queueUserExisting)
                } else if (msg.hasVoice()) {
                    message = QueueMessage(msg.text, msg.voice.fileId, MessageType.VOICE, queueUserExisting)
                } else if (msg.hasVideo()) {
                    message = QueueMessage(msg.text, msg.video.fileId, MessageType.VIDEO, queueUserExisting)
                }
                messageRepository.save(message)
            }
        }
    }

    private fun handleTextMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val replyMessage = if (msg.isReply) {
            val userMessageByResponseMessageId = userMessageRepository.findByResponseMessageId(
                msg.replyToMessage.messageId
            )
            val userMessageByMessageId = userMessageRepository.findByMessageId(
                msg.replyToMessage.messageId
            )
            val replyMessage = SendMessage()
            replyMessage.chatId = connectedSupporter.chatId!!
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
            SendMessage(connectedSupporter.chatId!!, msg.text)
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

    private fun handleVideoNoteMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val file = absSender.fileSavetoFolder(msg.videoNote.fileId, "video")
        val sendVideo = SendVideoNote().apply {
            chatId = connectedSupporter.chatId!!
            videoNote = InputFile(file)
        }
        val responseMessage = absSender.execute(sendVideo)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VIDEO_NOTE,
                msg.text,
                msg.videoNote.fileId
            )
        )
    }

    private fun handleStickerMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val file = absSender.fileSavetoFolder(msg.sticker.fileId, "stiker")
        val sticker = SendSticker().apply {
            chatId = connectedSupporter.chatId!!
            sticker = InputFile(file)
        }
        val responseMessage = absSender.execute(sticker)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.STICKER,
                msg.text,
                msg.sticker.fileId
            )
        )
    }

    private fun handleDocumentMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val file = absSender.fileSavetoFolder(msg.document.fileId, msg.document.fileName)
        val document = SendDocument()
        document.chatId = connectedSupporter.chatId!!
        document.document = InputFile(file)
        val responseMessage = absSender.execute(document)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.DOCUMENT,
                msg.text,
                msg.document.fileId
            )
        )
    }

    private fun handlePhotoMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val photo = msg.photo.last()
        val tempPhoto = absSender.fileSavetoFolder(photo.fileId, "image.png")
        val sendPhoto = SendPhoto()
        sendPhoto.chatId = connectedSupporter.chatId!!
        sendPhoto.photo = InputFile(tempPhoto)
        val responseMessage = absSender.execute(sendPhoto)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.PHOTO,
                msg.text,
                msg.photo.last.fileId
            )
        )
    }

    private fun handleVoiceMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val voice = absSender.fileSavetoFolder(msg.voice.fileId, "image.png")
        val sendVoice = SendVoice()
        sendVoice.voice = InputFile(voice)
        sendVoice.chatId = connectedSupporter.chatId!!
        val responseMessage = absSender.execute(sendVoice)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VOICE,
                msg.text,
                msg.voice.fileId
            )
        )
    }

    private fun handleVideoMessage(msg: Message, connectedSupporter: User, absSender: AbsSender) {
        val video = absSender.fileSavetoFolder(msg.video.fileId, msg.video.fileName ?: "video.mp4")
        val sendVideo = SendVideo()
        sendVideo.video = InputFile(video)
        sendVideo.chatId = connectedSupporter.chatId!!
        val responseMessage =  absSender.execute(sendVideo)
        userMessageRepository.save(
            UserMessage(
                msg.chatId,
                msg.messageId,
                responseMessage.messageId,
                MessageType.VIDEO,
                msg.text,
                msg.video.fileId
            )
        )
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

}


