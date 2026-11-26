package echo.music.desktop.system

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsMediaControlsTest {
    @Test
    fun parameterizedInterfaceIdsMatchWindows() {
        // IIterable<String>: a documented id, checks the UUID v5 algorithm itself.
        assertEquals(
            "{E2FCC7C1-3BFC-5A0B-B2B0-72E769D1CB7E}",
            WindowsMediaControls.pinterfaceIid("pinterface({faa585ea-6214-4217-afda-7f46de5869b3};string)").toGuidString()
        )
        // TypedEventHandler<SystemMediaTransportControls, SystemMediaTransportControlsButtonPressedEventArgs>
        assertEquals("{0557E996-7B23-5BAE-AA81-EA0D671143A4}", WindowsMediaControls.IID_BUTTON_HANDLER.toGuidString())
    }

    @Test
    fun coversAreRequestedBig() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-l90-rj",
            WindowsMediaControls.highResThumbnail("https://lh3.googleusercontent.com/abc=w60-h60-l90-rj")
        )
        assertEquals("https://i.ytimg.com/vi/x/hqdefault.jpg", WindowsMediaControls.highResThumbnail("https://i.ytimg.com/vi/x/hqdefault.jpg"))
    }
}
