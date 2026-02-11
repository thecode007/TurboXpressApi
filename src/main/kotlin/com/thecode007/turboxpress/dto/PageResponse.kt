package com.thecode007.turboxpress.dto

import org.springframework.data.domain.Page

data class PageResponse<T : Any>(
    val content: List<T>,
    val pageNumber: Int,
    val pageSize: Int,
    val totalElements: Long,
    val totalPages: Int,
    val isLast: Boolean
) {
    companion object {
        fun <T : Any> from(page: Page<T>): PageResponse<T> {
            return PageResponse(
                content = page.content,
                pageNumber = page.number,
                pageSize = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                isLast = page.isLast
            )
        }
    }
}
