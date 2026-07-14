package com.autoexpense.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object CustomCategoryRepository {
    private var dao: CustomCategoryDao? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val _customCategories = MutableStateFlow<List<CustomCategoryEntity>>(emptyList())
    val customCategories: StateFlow<List<CustomCategoryEntity>> = _customCategories.asStateFlow()

    fun init(customCategoryDao: CustomCategoryDao) {
        dao = customCategoryDao
        scope.launch {
            customCategoryDao.getAllFlow().collect { list ->
                _customCategories.value = list
            }
        }
    }

    fun addCategory(name: String, iconName: String, onComplete: (() -> Unit)? = null) {
        val currentDao = dao
        val cleanName = name.trim()
        if (currentDao != null) {
            scope.launch {
                currentDao.insert(CustomCategoryEntity(name = cleanName, iconName = iconName))
                val updated = currentDao.getAll()
                _customCategories.value = updated
                onComplete?.invoke()
            }
        } else {
            // Fallback for tests or uninitialized repo
            val newList = _customCategories.value.toMutableList()
            if (newList.none { it.name.equals(cleanName, ignoreCase = true) }) {
                newList.add(CustomCategoryEntity(id = newList.size + 100, name = cleanName, iconName = iconName))
                _customCategories.value = newList
            }
            onComplete?.invoke()
        }
    }
}
