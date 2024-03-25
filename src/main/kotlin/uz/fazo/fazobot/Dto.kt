package uz.fazo.fazobot

import jakarta.validation.constraints.NotEmpty

class UserDto(
    @field:NotEmpty
    val chatId: String,
    @field:NotEmpty
    var firstName: String,
    @field:NotEmpty
    var lastName: String,
    @field:NotEmpty
    var lang: MutableSet<UserLang>,
)


class Result(
    val message: String
)