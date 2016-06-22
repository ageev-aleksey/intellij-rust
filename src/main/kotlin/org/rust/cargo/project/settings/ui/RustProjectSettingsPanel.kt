package org.rust.cargo.project.settings.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Version
import org.rust.cargo.toolchain.suggestToolchain
import javax.swing.*
import javax.swing.event.DocumentEvent

class RustProjectSettingsPanel : JPanel() {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean
    ) {
        fun applyTo(settings: RustProjectSettingsService) {
            settings.autoUpdateEnabled = autoUpdateEnabled
            settings.toolchain = toolchain
        }
    }

    private val disposable = Disposer.newDisposable()
    @Suppress("unused") // required by GUI designer to use this form as an element of other forms
    private lateinit var root: JPanel
    private lateinit var toolchainLocationField: TextFieldWithBrowseButton

    private lateinit var autoUpdateEnabled: JCheckBox
    private lateinit var rustVersion: JLabel
    private lateinit var versionUpdateAlarm: Alarm

    private val versionUpdateDelayMillis = 200

    var data: Data
        get() = Data(
            RustToolchain(toolchainLocationField.text),
            autoUpdateEnabled.isSelected
        )
        set(value) {
            toolchainLocationField.text = value.toolchain?.location
            autoUpdateEnabled.isSelected = value.autoUpdateEnabled
        }

    init {
        toolchainLocationField.addBrowseFolderListener(
            "",
            "Cargo location",
            null,
            FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor(),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
            false
        )
        versionUpdateAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
        listenForUpdates(toolchainLocationField.textField)
        Disposer.register(disposable, toolchainLocationField)


        data = Data(
            suggestToolchain(),
            autoUpdateEnabled = true
        )
    }

    fun disposeUIResources() {
        Disposer.dispose(disposable)
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        } else if (!toolchain.containsMetadataCommand()) {
            throw ConfigurationException("Configured toolchain is Incompatible with the plugin: required at least ${RustToolchain.CARGO_LEAST_COMPATIBLE_VERSION}, found ${toolchain.queryCargoVersion()}")
        }
    }

    private fun listenForUpdates(textField: JTextField) {
        var previousLocation = textField.text

        textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent?) {
                val currentLocation = textField.text
                if (currentLocation != previousLocation) {
                    scheduleVersionUpdate(currentLocation)
                    previousLocation = currentLocation
                }
            }
        })
    }

    private fun scheduleVersionUpdate(toolchainLocation: String) {
        versionUpdateAlarm.cancelAllRequests()
        versionUpdateAlarm.addRequest({
            val version = RustToolchain(toolchainLocation).queryRustcVersion()
            updateVersion(version?.release)
        }, versionUpdateDelayMillis)
    }

    private fun updateVersion(newVersion: String?) {
        ApplicationManager.getApplication().invokeLater({
            if (!Disposer.isDisposed(disposable)) {
                val isInvalid = newVersion == null
                rustVersion.text = if (isInvalid) "N/A" else newVersion
                rustVersion.foreground = if (isInvalid) JBColor.RED else JBColor.foreground()
            }
        }, ModalityState.any())
    }
}

