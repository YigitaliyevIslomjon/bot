package uz.fazo.fazobot

import jakarta.persistence.*

@Entity(name = "users")
class User(
    var firstName: String? = null,
    var lastName: String? = null,
    @Enumerated(EnumType.STRING)
    var lang: MutableSet<UserLang> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    var interFaceLang: UserLang = UserLang.UZ,
    @Column(nullable = false)
    var role: String = ROLE.USER,
    @Column(unique = true)
    val chatId: String? = null,
    @Column(unique = true)
    var clientId: String? = null,
    @Column(nullable = false)
    var active: Boolean = false,
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var registerState: RegisterState = RegisterState.START,
    @Enumerated(EnumType.STRING)
    var state: SupporterState = SupporterState.FREE,
    @Column(nullable = false)
    var supporterMessageSent: Boolean = false,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)

@Entity()
class SupporterAndUserMark(
    @ManyToOne(fetch = FetchType.EAGER)
    val supporter: User,
    @ManyToOne(fetch = FetchType.EAGER)
    val clientUser: User,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)

@Entity()
class SupporterMark(
    @Column(nullable = false)
    var mark: String,
    @ManyToOne()
    val clientUser: User,
    @ManyToOne()
    val supporterUser: User,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)


@Entity
class QueueMessage(
    val message: String? = null,
    val fileId: String? = null,
    @Enumerated(EnumType.STRING)
    val type: MessageType? = null,
    @ManyToOne(fetch = FetchType.EAGER)
    var queueUser: QueueUser? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)

@Entity
class QueueUser(
    val chatId: String,
    @OneToMany(mappedBy = "queueUser", cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var message: List<QueueMessage> = mutableListOf(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)

@Entity
class UserMessage(
    @Column(nullable = false)
    val chatId: Long,
    @Column(nullable = false)
    val messageId: Int,
    @Column(nullable = false)
    val responseMessageId: Int,
    @Enumerated(EnumType.STRING)
    val messageType: MessageType,
    val message: String? = null,
    val fileId: String? = null,
    val fileName: String? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null
)
