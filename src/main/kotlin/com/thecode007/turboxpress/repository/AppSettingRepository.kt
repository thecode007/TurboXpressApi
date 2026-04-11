package com.thecode007.turboxpress.repository

import com.thecode007.turboxpress.entity.AppSetting
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AppSettingRepository : JpaRepository<AppSetting, Long>
