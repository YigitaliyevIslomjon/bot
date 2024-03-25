package uz.fazo.fazobot

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    fun findByChatId(chatId: String): User?
    fun findAllByRole(role: String): List<User>?
    fun findByClientId(clientId: String): User?
}

interface MessageRepository : JpaRepository<QueueMessage, Long> {
    @Query(value = "select * from queue_message m where m.queue_user_id= :userId", nativeQuery = true)
    fun findAllByUserId(userId: Long): List<QueueMessage>
}

interface QueueUserRepository : JpaRepository<QueueUser, Long> {
    fun findByChatId(chatId: String): QueueUser?
}

interface SupporterMarkRepository : JpaRepository<SupporterMark, Long> {
    fun findByClientUser(clientUser: User): SupporterMark?
}

interface SupporterAndUserMarkRepository : JpaRepository<SupporterAndUserMark, Long> {
    fun findFirstBySupporter(supporter: User): SupporterAndUserMark?
}

interface UserMessageRepository : JpaRepository<UserMessage, Long> {
    fun findByMessageId(messageId: Int): UserMessage?
    fun findByResponseMessageId(responseMessageId: Int): UserMessage?
}
