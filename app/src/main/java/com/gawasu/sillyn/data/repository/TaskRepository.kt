package com.gawasu.sillyn.data.repository

import javax.inject.Inject

class TaskRepository @Inject constructor(
    taskRepositoryImpl: TaskRepositoryImpl
) : TaskRepositoryInterface by taskRepositoryImpl