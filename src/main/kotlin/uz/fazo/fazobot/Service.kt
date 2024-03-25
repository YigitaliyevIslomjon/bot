package uz.fazo.fazobot

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi

import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.bots.AbsSender
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession
import uz.fazo.fazobot.messageHandlers.MessageHandler


interface UpdateHandler {
    fun handle(update: Update, absSender: AbsSender)
}


interface UserService {
    fun add(dto: UserDto): Result
    fun edit(dto: UserDto): Result
    fun delete(id: Long): Result
    fun getOne(id: Long): User
    fun getAll(): List<User>
}

@Component
class TelegramBotInitializer(private val mainBot: MainBot) {
    init {
        try {
            val telegramBotsApi = TelegramBotsApi(DefaultBotSession::class.java)
            telegramBotsApi.registerBot(mainBot)
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}

@Service
class MainBot(
    @Value("\${telegram-bot.token}") token: String,
    @Value("\${telegram-bot.username}") val username: String,
    private val updateHandler: UpdateHandler

) : TelegramLongPollingBot(token) {

    override fun getBotUsername() = username

    override fun onUpdateReceived(update: Update) {
        updateHandler.handle(update, this)
    }
}

@Service
class UpdateHandlerImpl(
    private val messageHandler: MessageHandler,
    private val callbackHandler: CallbackHandler
) : UpdateHandler {

    override fun handle(update: Update, absSender: AbsSender) {
        when {
            update.hasMessage() -> messageHandler.handle(update.message, absSender)
            update.hasCallbackQuery() -> callbackHandler.handle(update.callbackQuery, absSender)
        }
    }
}

@Service
class UserServiceImpl(private val userRepository: UserRepository) : UserService {
    override fun add(dto: UserDto) = dto.run {
        val user  = userRepository.findByChatId(chatId)
        if(user != null){
            throw  IllegalArgumentException("this chatId $chatId supporter already exist")
        }
        userRepository.save(
            User(
                firstName = firstName,
                lastName = lastName,
                chatId = chatId,
                lang = lang,
                role = ROLE.SUPPORTER,
                registerState = RegisterState.FINISH
            )
        )
        Result("date are saved successfully")
    }

    override fun edit(dto: UserDto): Result {
        TODO("Not yet implemented")
    }

    override fun delete(id: Long): Result {
        TODO("Not yet implemented")
    }

    override fun getOne(id: Long): User {
        TODO("Not yet implemented")
    }

    override fun getAll(): List<User> {
        TODO("Not yet implemented")
    }

}


