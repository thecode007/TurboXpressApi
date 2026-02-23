package com.thecode007.turboxpress.service

import com.thecode007.turboxpress.dto.CreateRestaurantItemRequest
import com.thecode007.turboxpress.dto.RestaurantItemResponse
import com.thecode007.turboxpress.dto.UpdateRestaurantItemRequest
import com.thecode007.turboxpress.entity.RestaurantItem
import com.thecode007.turboxpress.repository.RestaurantItemRepository
import com.thecode007.turboxpress.repository.RestaurantRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RestaurantItemService(
    private val restaurantItemRepository: RestaurantItemRepository,
    private val restaurantRepository: RestaurantRepository
) {

    @Transactional
    fun addItemToRestaurant(restaurantId: Long, request: CreateRestaurantItemRequest): RestaurantItemResponse {
        val restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow { IllegalArgumentException("Restaurant not found with id: $restaurantId") }

        val item = RestaurantItem(
            title = request.title,
            description = request.description,
            price = request.price,
            photoUrls = request.photoUrls.toMutableList(),
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
}
