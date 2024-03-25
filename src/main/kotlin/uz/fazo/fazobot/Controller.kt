package uz.fazo.fazobot

import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/supporter")
class UserController(
    private val userService: UserService
) {
    @RequestMapping("add")
    fun add(@RequestBody dto: UserDto): Result = userService.add(dto)
}