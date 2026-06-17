package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateCategoryRequest
import com.thecode007.turboxpress.dto.RestaurantCategoryResponse
import com.thecode007.turboxpress.entity.RestaurantCategory
import com.thecode007.turboxpress.repository.RestaurantCategoryRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantCategoryService(
    private val categoryRepository: RestaurantCategoryRepository,
    private val restaurantRepository: RestaurantRepository
) {

    @Transactional
    fun createCategoryForOwner(ownerPhoneNumber: String, request: CreateCategoryRequest): RestaurantCategoryResponse {
        val restaurant = restaurantRepository.findFirstByOwnerPhoneNumber(ownerPhoneNumber)
            .orElseThrow { IllegalArgumentException("Restaurant not found for owner: $ownerPhoneNumber") }

        val name = request.name.trim()
        val existing = categoryRepository.findByRestaurantIdAndNameIgnoreCase(restaurant.id, name)
        if (existing.isPresent) {
            return RestaurantCategoryResponse.from(existing.get())
        }

        val category = RestaurantCategory(name = name, restaurant = restaurant)
        val saved = categoryRepository.save(category)
        return RestaurantCategoryResponse.from(saved)
    }
}
