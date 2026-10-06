package com.raja.bingehwa.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import com.raja.bingehwa.AppContainer
import com.raja.bingehwa.BingeApp

/** Gives every ViewModel the app container, which replaces a DI framework here. */
class ContainerVmFactory<T : ViewModel>(private val make: (AppContainer) -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V {
        val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as BingeApp
        return make(app.container) as V
    }
}

/** [key] must differ per argument (for example the series id) so each screen gets its own instance. */
@Composable
inline fun <reified T : ViewModel> containerViewModel(key: String? = null, noinline make: (AppContainer) -> T): T =
    viewModel(key = key, factory = ContainerVmFactory(make))
