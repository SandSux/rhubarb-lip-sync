package com.rhubarb_lip_sync.rhubarb_for_spine

import javafx.beans.binding.Bindings
import javafx.beans.property.Property
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.*
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.text.Text
import javafx.stage.FileChooser
import tornadofx.*
import java.io.File
import java.util.concurrent.Executors

class MainView : View() {
	private val executor = Executors.newSingleThreadExecutor()
	private val mainModel = MainModel(executor)

	init {
		title = "Rhubarb Lip Sync for Spine"
	}

	override val root = form {
		var filePathTextField: TextField? = null
		var filePathButton: Button? = null

		val fileModelProperty = mainModel.animationFileModelProperty

		minWidth = 800.0
		prefWidth = 1000.0
		fieldset("Settings") {
			disableProperty().bind(fileModelProperty.select { it!!.busyProperty })
			field("Spine JSON file") {
				filePathTextField = textfield {
					textProperty().bindBidirectional(mainModel.filePathStringProperty)
					errorProperty().bind(mainModel.filePathErrorProperty)
				}
				filePathButton = button("...")
			}
			field("Mouth slot") {
				combobox<String> {
					itemsProperty().bind(fileModelProperty.select { it!!.slotsProperty })
					valueProperty().bindBidirectional(fileModelProperty.select { it!!.mouthSlotProperty })
					errorProperty().bind(fileModelProperty.select { it!!.mouthSlotErrorProperty })
				}
			}
			field("Mouth naming") {
				label {
					textProperty().bind(
						fileModelProperty
							.select { it!!.mouthNamingProperty }
							.select { SimpleStringProperty(it.displayString) }
					)
				}
			}
			field("Mouth shapes") {
				hbox {
					label {
						textProperty().bind(
							fileModelProperty
								.select { it!!.mouthShapesProperty }
								.select {
									val result = if (it.isEmpty()) "none" else it.joinToString()
									SimpleStringProperty(result)
								}
						)
					}
					errorProperty().bind(fileModelProperty.select { it!!.mouthShapesErrorProperty })
				}
			}
			field("Animation naming") {
				textfield {
					maxWidth = 100.0
					textProperty().bindBidirectional(mainModel.animationPrefixProperty)
				}
				label("<audio event name>")
				textfield {
					maxWidth = 100.0
					textProperty().bindBidirectional(mainModel.animationSuffixProperty)
				}
			}
		}
		fieldset("Audio events") {
			tableview<AudioFileModel> {
				placeholder = Label("There are no events with associated audio files.")
				columnResizePolicy = SmartResize.POLICY
				column("Event", AudioFileModel::eventNameProperty)
					.weigthedWidth(1.0)
				column("Animation name", AudioFileModel::animationNameProperty)
					.weigthedWidth(1.0)
				column("Audio file", AudioFileModel::displayFilePathProperty)
					.weigthedWidth(1.0)
				column("Dialog", AudioFileModel::dialogProperty).apply {
					weigthedWidth(3.0)
					// Make dialog column wrap
					setCellFactory { tableColumn ->
						return@setCellFactory TableCell<AudioFileModel, String>().also { cell ->
							cell.graphic = Text().apply {
								textProperty().bind(cell.itemProperty())
								fillProperty().bind(cell.textFillProperty())
								val widthProperty = tableColumn.widthProperty()
									.minus(cell.paddingLeftProperty)
									.minus(cell.paddingRightProperty)
								wrappingWidthProperty().bind(widthProperty)
							}
							cell.prefHeight = Control.USE_COMPUTED_SIZE
						}
					}
				}
				column("Status", AudioFileModel::statusLabelProperty)
					.weigthedWidth(1.0)
				column("", AudioFileModel::actionLabelProperty).apply {
					weigthedWidth(1.0)
					// Show button
					setCellFactory { tableColumn ->
						return@setCellFactory object : TableCell<AudioFileModel, String>() {
							override fun updateItem(item: String?, empty: Boolean) {
								super.updateItem(item, empty)
								graphic = if (!empty)
									Button(item).apply {
										this.maxWidth = Double.MAX_VALUE
										setOnAction {
											val audioFileModel = this@tableview.items[index]
											audioFileModel.performAction()
										}
										val invalidProperty: Property<Boolean> = fileModelProperty
											.select { it!!.validProperty }
											.select { SimpleBooleanProperty(!it) }
										disableProperty().bind(invalidProperty)
									}
								else
									null
							}
						}
					}
				}
				itemsProperty().bind(fileModelProperty.select { it!!.audioFileModelsProperty })
			}
		}

		onDragOver = EventHandler<DragEvent> { event ->
			if (event.dragboard.hasFiles() && mainModel.animationFileModel?.busy != true) {
				event.acceptTransferModes(TransferMode.COPY)
				event.consume()
			}
		}
		onDragDropped = EventHandler<DragEvent> { event ->
			if (event.dragboard.hasFiles() && mainModel.animationFileModel?.busy != true) {
				filePathTextField!!.text = event.dragboard.files.firstOrNull()?.path
				event.isDropCompleted = true
				event.consume()
			}
		}

		whenUndocked {
			executor.shutdownNow()
		}

		filePathButton!!.onAction = EventHandler<ActionEvent> {
			val fileChooser = FileChooser().apply {
				title = "Open Spine JSON file"
				extensionFilters.addAll(
					FileChooser.ExtensionFilter("Spine JSON file (*.json)", "*.json"),
					FileChooser.ExtensionFilter("All files (*.*)", "*.*")
				)
				val lastDirectory = filePathTextField!!.text?.let { File(it).parentFile }
				if (lastDirectory != null && lastDirectory.isDirectory) {
					initialDirectory = lastDirectory
				}
			}
			val file = fileChooser.showOpenDialog(this@MainView.primaryStage)
			if (file != null) {
				filePathTextField!!.text = file.path
			}
		}
	}
}