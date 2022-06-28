package me.leon.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.geometry.Pos
import javafx.scene.control.*
import me.leon.Styles
import me.leon.WIKI_CTF
import me.leon.controller.ClassicalController
import me.leon.encode.base.base64
import me.leon.ext.*
import me.leon.ext.crypto.*
import me.leon.ext.fx.*
import tornadofx.*
import tornadofx.FX.Companion.messages

class ClassicalView : Fragment(messages["classical"]) {
    private val controller: ClassicalController by inject()

    private var timeConsumption = 0L
    private var startTime = 0L
    private var isEncrypt = true
    private var encodeType = ClassicalCryptoType.CAESAR

    override val closeable = SimpleBooleanProperty(false)
    private val isSingleLine = SimpleBooleanProperty(false)
    private val decodeIgnoreSpace = SimpleBooleanProperty(encodeType.isIgnoreSpace())
    private val param1Enabled = SimpleBooleanProperty(encodeType.paramsCount() > 0)
    private val param2Enabled = SimpleBooleanProperty(encodeType.paramsCount() > 1)
    private val isProcessing = SimpleBooleanProperty(false)

    private var taInput: TextArea by singleAssign()
    private var taOutput: TextArea by singleAssign()
    private var tfParam1: TextField by singleAssign()
    private var tfParam2: TextField by singleAssign()
    private var labelInfo: Label by singleAssign()

    private val info: String
        get() =
            "${if (isEncrypt) messages["encode"] else messages["decode"]}: $encodeType  ${messages["inputLength"]}:" +
                " ${inputText.length}  ${messages["outputLength"]}: ${outputText.length} cost: $timeConsumption ms"
    private val inputText: String
        get() = taInput.text.takeUnless { decodeIgnoreSpace.get() } ?: taInput.text.stripAllSpace()
    private val outputText: String
        get() = taOutput.text

    private val cryptoParams
        get() = mapOf("p1" to tfParam1.text, "p2" to tfParam2.text)

    private val eventHandler = fileDraggedHandler {
        taInput.text =
            with(it.first()) {
                if (length() <= 128 * 1024) {
                    if (realExtension() in unsupportedExts) "unsupported file extension"
                    else readText()
                } else "not support file larger than 128KB"
            }
    }
    private val centerNode = vbox {
        addClass(Styles.group)
        hbox {
            spacing = DEFAULT_SPACING
            label(messages["input"])
            button(graphic = imageview("/img/openwindow.png")) {
                tooltip(messages["newWindow"])
                action { find<ClassicalView>().openWindow() }
            }
            button(graphic = imageview("/img/import.png")) {
                tooltip(messages["pasteFromClipboard"])
                action { taInput.text = clipboardText() }
            }
        }

        taInput =
            textarea {
                promptText = messages["inputHint"]
                isWrapText = true
                onDragEntered = eventHandler
                contextmenu {
                    item(messages["loadFromNet"]) {
                        action { runAsync { inputText.readFromNet() } ui { taInput.text = it } }
                    }
                    item(messages["loadFromNetLoop"]) {
                        action {
                            runAsync { inputText.simpleReadFromNet() } ui { taInput.text = it }
                        }
                    }
                    item(messages["loadFromNet2"]) {
                        action {
                            runAsync { inputText.readBytesFromNet().base64() } ui
                                {
                                    taInput.text = it
                                }
                        }
                    }
                    item(messages["readHeadersFromNet"]) {
                        action {
                            runAsync { inputText.readHeadersFromNet() } ui { taInput.text = it }
                        }
                    }
                }
            }
        hbox {
            addClass(Styles.left)
            label("${messages["encrypt"]}:")
            tilepane {
                vgap = 8.0
                alignment = Pos.TOP_LEFT
                prefColumns = 7
                togglegroup {
                    classicalTypeMap.forEach {
                        radiobutton(it.key) {
                            setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
                            if (it.value == ClassicalCryptoType.CAESAR) isSelected = true
                        }
                    }
                    selectedToggleProperty().addListener { _, _, new ->
                        encodeType = new.cast<RadioButton>().text.classicalType()
                        param1Enabled.set(encodeType.paramsCount() > 0)
                        param2Enabled.set(encodeType.paramsCount() > 1)
                        tfParam1.promptText = encodeType.paramsHints()[0]
                        tfParam2.promptText = encodeType.paramsHints()[1]
                        decodeIgnoreSpace.set(encodeType.isIgnoreSpace())

                        if (isEncrypt) run()
                        else {
                            timeConsumption = 0
                            labelInfo.text = info
                        }
                    }
                }
            }
        }
        hbox {
            spacing = DEFAULT_SPACING
            alignment = Pos.BASELINE_CENTER
            tfParam1 =
                textfield {
                    prefWidth = DEFAULT_SPACING_40X
                    promptText = encodeType.paramsHints()[0]
                    visibleWhen(param1Enabled)
                }
            tfParam2 =
                textfield {
                    prefWidth = DEFAULT_SPACING_40X
                    promptText = encodeType.paramsHints()[1]
                    visibleWhen(param2Enabled)
                }
        }

        hbox {
            spacing = DEFAULT_SPACING
            alignment = Pos.CENTER
            togglegroup {
                spacing = DEFAULT_SPACING
                alignment = Pos.CENTER
                radiobutton(messages["encrypt"]) { isSelected = true }
                radiobutton(messages["decrypt"])
                checkbox(messages["decodeIgnoreSpace"], decodeIgnoreSpace)
                checkbox(messages["singleLine"], isSingleLine)
                selectedToggleProperty().addListener { _, _, new ->
                    isEncrypt = new.cast<RadioButton>().text == messages["encrypt"]
                    run()
                }
            }
            button(messages["run"], imageview("/img/run.png")) {
                action { run() }
                enableWhen(!isProcessing)
            }
            button(messages["codeFrequency"]) { action { "https://quipqiup.com/".openInBrowser() } }
            button("wiki") { action { WIKI_CTF.openInBrowser() } }
        }
        hbox {
            spacing = DEFAULT_SPACING
            label(messages["output"])
            button(graphic = imageview("/img/copy.png")) {
                tooltip(messages["copy"])
                action { outputText.copy() }
            }
            button(graphic = imageview("/img/up.png")) {
                tooltip(messages["up"])
                action {
                    taInput.text = outputText
                    taOutput.text = ""
                }
            }
        }

        taOutput =
            textarea {
                promptText = messages["outputHint"]
                isWrapText = true
                contextmenu {
                    item("uppercase") { action { taOutput.text = taOutput.text.uppercase() } }
                    item("lowercase") { action { taOutput.text = taOutput.text.lowercase() } }
                    item("reverse") {
                        action {
                            taOutput.text =
                                taOutput.text.split("\r\n|\n".toRegex()).joinToString("\r\n") {
                                    it.reversed()
                                }
                        }
                    }

                    item("clear") { action { taOutput.text = "" } }
                }
            }
    }

    override val root = borderpane {
        center = centerNode
        bottom = hbox { labelInfo = label(info) }
    }

    private fun run() {
        isProcessing.value = true
        startTime = System.currentTimeMillis()
        runAsync {
            if (isEncrypt) {
                controller.encrypt(
                    inputText,
                    encodeType,
                    cryptoParams,
                    isSingleLine.get(),
                )
            } else controller.decrypt(inputText, encodeType, cryptoParams, isSingleLine.get())
        } ui
            {
                isProcessing.value = false
                taOutput.text = it
                if (Prefs.autoCopy) {
                    outputText.copy().also { primaryStage.showToast(messages["copied"]) }
                }
                timeConsumption = System.currentTimeMillis() - startTime
                labelInfo.text = info
            }
    }
}
