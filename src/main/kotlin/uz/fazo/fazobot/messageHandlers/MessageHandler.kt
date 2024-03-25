package uz.fazo.fazobot.messageHandlers

import org.springframework.stereotype.Service
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.bots.AbsSender
import uz.fazo.fazobot.*


interface MessageHandler {
    fun handle(msg: Message, absSender: AbsSender)
}


@Service
class MessageHandlerImpl(
    private val userRepository: UserRepository,
    private val handlerUserRole: HandlerUserRole,
    private val handlerSupportRole: HandlerSupportRole,

    ) : MessageHandler {

    override fun handle(msg: Message, absSender: AbsSender) {
        val chatId = msg.chatId.toString()
        val currentUser = findOrCreateUser(chatId)

        when (currentUser.role) {
            ROLE.USER -> handlerUserRole.handle(msg, currentUser, absSender)
            ROLE.SUPPORTER -> handlerSupportRole.handle(msg, currentUser, absSender)
        }

    }

    private fun findOrCreateUser(chatId: String): User =
        userRepository.findByChatId(chatId) ?: userRepository.save(User(chatId = chatId))

}