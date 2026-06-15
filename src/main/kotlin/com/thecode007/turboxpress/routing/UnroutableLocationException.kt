package com.thecode007.turboxpress.routing

class UnroutableLocationException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
