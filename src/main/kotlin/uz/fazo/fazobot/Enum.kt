package uz.fazo.fazobot

enum class RegisterState {
    START,
    ENTER_FIRST_NAME,
    ENTER_LAST_NAME,
    SELECT_LANG,
    SELECT_INTERFACE_LANG,
    REGISTERED,
    DONE,
    FINISH
}



enum class SupporterState {
    FREE,
    BUSY
}

enum class UserLang {
    UZ,
    RU,
    ENG
}

object LANG {
    const val UZ = "\uD83C\uDDFA\uD83C\uDDFF UZ"
    const val RU = "\uD83C\uDDF7\uD83C\uDDFA RU"
    const val ENG = "\uD83C\uDDFA\uD83C\uDDF8 ENG"
}

object ROLE {
    const val USER = "USER"
    const val SUPPORTER = "SUPPORTER"
}

enum  class MarkValue {
    ONE,
    TWO,
   THREE,
   FOUR,
    FIVE,
}

enum class MessageType{
    DOCUMENT,
    PHOTO,
    VIDEO_NOTE,
    VIDEO,
    STICKER,
    MESSAGE,
    VOICE
}


