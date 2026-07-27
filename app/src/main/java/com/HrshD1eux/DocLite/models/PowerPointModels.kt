package com.HrshD1eux.DocLite.models

enum class ElementType {
    TITLE, SUBTITLE, BODY_TEXT, IMAGE, SHAPE
}

data class SlideElement(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ElementType,
    val textContent: String = "",
    val imageUri: String? = null,
    val positionXDp: Float = 16f,
    val positionYDp: Float = 16f,
    val fontSizeSp: Float = 18f,
    val textColorHex: String = "#1C1B1F"
)

data class Slide(
    val id: String = java.util.UUID.randomUUID().toString(),
    val slideNumber: Int,
    val title: String = "Slide $slideNumber",
    val elements: List<SlideElement> = listOf(
        SlideElement(type = ElementType.TITLE, textContent = "Title", fontSizeSp = 28f),
        SlideElement(type = ElementType.BODY_TEXT, textContent = "Tap to edit text", fontSizeSp = 18f)
    ),
    val backgroundColorHex: String = "#FFFFFF"
)

data class PresentationDocument(
    val title: String,
    val fileUri: String,
    val slides: List<Slide> = listOf(Slide(slideNumber = 1))
)

