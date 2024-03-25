package uz.fazo.fazobot

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.CallbackQuery
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.bots.AbsSender
import java.lang.IllegalArgumentException


interface CallbackHandler {
    fun handle(callback: CallbackQuery, absSender: AbsSender)
}

@Service
class CallbackHandlerImpl(
    private val userRepository: UserRepository,
    private val supporterMarkRepository: SupporterMarkRepository,
    private val supporterAndUserMarkRepository: SupporterAndUserMarkRepository,
    private val userMessageRepository: UserMessageRepository,
) : CallbackHandler {
    override fun handle(callback: CallbackQuery, absSender: AbsSender) {
        val chatId = callback.from.id.toString()
        val currentUser = findUser(chatId)

        when {
            callback.data == LANG.UZ -> {
                val message =
                    "Interface tilini tanlang \n" +
                            "Выберите язык интерфейса \n" +
                            "Select the interface language"
                absSender.sendMessageMarkUpSupport(
                    currentUser,
                    chatId,
                    message,
                    callback.message.messageId,
                    UserLang.UZ
                )
            }

            callback.data == LANG.RU -> {
                val message =
                    "Interface tilini tanlang \n" +
                            "Выберите язык интерфейса \n" +
                            "Select the interface language"
                absSender.sendMessageMarkUpSupport(
                    currentUser,
                    chatId,
                    message,
                    callback.message.messageId,
                    UserLang.RU
                )
            }

            callback.data == LANG.ENG -> {
                val message =
                    "Interface tilini tanlang \n" +
                            "Выберите язык интерфейса \n" +
                            "Select the interface language"
                absSender.sendMessageMarkUpSupport(
                    currentUser,
                    chatId,
                    message,
                    callback.message.messageId,
                    UserLang.ENG
                )
            }

            callback.data == "DONE" -> {
                absSender.answerCallbackQuery(callback.id, "done")
                absSender.sendMessage(chatId, "language are changed")
                absSender.deleteMessage(chatId, callback.message.messageId)
            }

            callback.data == MarkValue.ONE.toString()
                    || callback.data == MarkValue.TWO.toString()
                    || callback.data == MarkValue.THREE.toString()
                    || callback.data == MarkValue.FOUR.toString()
                    || callback.data == MarkValue.FIVE.toString() -> {
                val message = ThankYouMark[currentUser.lang.first()]
                val supporterAndUserMark = supporterAndUserMarkRepository.findFirstBySupporter(currentUser)
                    ?: throw IllegalArgumentException("user ${currentUser.id} is not found")
                val mark = SupporterMark(callback.data, currentUser, supporterAndUserMark.supporter)

                supporterMarkRepository.save(mark)
                supporterAndUserMarkRepository.deleteById(supporterAndUserMark.id!!)
                absSender.answerCallbackQuery(callback.id, message!!)
                absSender.deleteMessage(chatId, callback.message.messageId)
            }
        }
    }

    private fun AbsSender.sendMessage(chatId: String, msg: String) {
        val sendMessage = SendMessage()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        this.execute(sendMessage)
    }

    private fun AbsSender.answerCallbackQuery(queryId: String, message: String, showAlert: Boolean = false) {
        val answer = AnswerCallbackQuery()
        answer.text = message
        answer.showAlert = showAlert
        answer.callbackQueryId = queryId
        this.execute(answer)
    }

    private fun findUser(chatId: String): User = userRepository.findByChatId(chatId) ?: User(chatId = chatId)

    private fun AbsSender.deleteMessage(chatId: String, messageId: Int) {
        val deleteMessage = DeleteMessage()
        deleteMessage.chatId = chatId
        deleteMessage.messageId = messageId
        this.execute(deleteMessage)
    }

    private fun AbsSender.sendMessageMarkUpSupport(
        user: User,
        chatId: String,
        msg: String,
        messageId: Int,
        lang: UserLang? = null
    ) {
        val sendMessage = EditMessageText()
        sendMessage.text = msg
        sendMessage.chatId = chatId
        sendMessage.messageId = messageId

        val inlineKeyboardMarkup = InlineKeyboardMarkup()
        val td: MutableList<InlineKeyboardButton> = mutableListOf()
        val tdSubmit: MutableList<InlineKeyboardButton> = mutableListOf()
        val inlineKeyboardButtonUZ = InlineKeyboardButton()
        val inlineKeyboardButtonRU = InlineKeyboardButton()
        val inlineKeyboardButtonENG = InlineKeyboardButton()
        val inlineKeyboardButtonDONE = InlineKeyboardButton()

        if (lang != null && user.lang.contains(lang)) {
            user.lang.remove(lang)
        } else if (lang != null) {
            user.lang.add(lang)
        }

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
        userRepository.save(user)
        execute(sendMessage)
    }


}
