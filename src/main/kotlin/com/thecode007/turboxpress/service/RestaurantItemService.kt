package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateRestaurantItemRequest
import com.thecode007.turboxpress.dto.RestaurantItemResponse
import com.thecode007.turboxpress.dto.UpdateRestaurantItemRequest
import com.thecode007.turboxpress.entity.RestaurantItem
import com.thecode007.turboxpress.repository.RestaurantItemRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import com.thecode007.turboxpress.repository.RestaurantCategoryRepository
import com.thecode007.turboxpress.entity.RestaurantCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantItemService(
    private val restaurantItemRepository: RestaurantItemRepository,
    private val restaurantRepository: RestaurantRepository,
    private val restaurantCategoryRepository: RestaurantCategoryRepository
) {

    @Transactional
    fun addItemToRestaurant(restaurantId: Long, request: CreateRestaurantItemRequest): RestaurantItemResponse {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $restaurantId") }

        val item = RestaurantItem(
            title = request.title,
            description = request.description,
            price = request.price,
            category = resolveCategory(restaurant, request.category),
            isAvailable = request.isAvailable,
            photoUrls = request.photoUrls?.toMutableList() ?: mutableListOf(),
            restaurant = restaurant
        )

        return RestaurantItemResponse.from(restaurantItemRepository.save(item))
    }

    @Transactional
    fun addItemToRestaurantByOwner(ownerPhoneNumber: String, request: CreateRestaurantItemRequest): RestaurantItemResponse {
        val restaurant = restaurantRepository.findFirstByOwnerPhoneNumber(ownerPhoneNumber)
            .orElseThrow { IllegalArgumentException("Restaurant not found for owner: $ownerPhoneNumber") }

        val item = RestaurantItem(
            title = request.title,
            description = request.description,
            price = request.price,
            category = resolveCategory(restaurant, request.category),
            isAvailable = request.isAvailable,
            photoUrls = request.photoUrls?.toMutableList() ?: mutableListOf(),
            restaurant = restaurant
        )

        return RestaurantItemResponse.from(restaurantItemRepository.save(item))
    }

    @Transactional
    fun updateItem(itemId: Long, request: UpdateRestaurantItemRequest): RestaurantItemResponse {
        val item = restaurantItemRepository.findById(itemId)
            .orElseThrow { IllegalArgumentException("Item not found with id: $itemId") }

        request.title?.let { item.title = it }
        request.description?.let { item.description = it }
        request.price?.let { item.price = it }
        request.category?.let { item.category = resolveCategory(item.restaurant, it) }
        request.isAvailable?.let { item.isAvailable = it }
        request.photoUrls?.let { item.photoUrls = it.toMutableList() }

        return RestaurantItemResponse.from(restaurantItemRepository.save(item))
    }

    @Transactional
    fun deleteItem(itemId: Long) {
        if (!restaurantItemRepository.existsById(itemId)) {
            throw IllegalArgumentException("Item not found with id: $itemId")
        }
        restaurantItemRepository.deleteById(itemId)
    }

    private fun resolveCategory(restaurant: com.thecode007.turboxpress.entity.Restaurant, categoryName: String?): RestaurantCategory? {
        if (categoryName.isNullOrBlank()) return null
        val name = categoryName.trim()
        val existing = restaurantCategoryRepository.findByRestaurantIdAndNameIgnoreCase(restaurant.id, name)
        if (existing.isPresent) {
            return existing.get()
        }
        val newCategory = RestaurantCategory(name = name, restaurant = restaurant)
        return restaurantCategoryRepository.save(newCategory)
    }
}
