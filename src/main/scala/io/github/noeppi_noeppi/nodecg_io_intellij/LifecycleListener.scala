package io.github.noeppi_noeppi.nodecg_io_intellij

import java.util

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.ui.Messages

class LifecycleListener extends AppLifecycleListener {

  override def appFrameCreated(commandLineArgs: util.List[String]): Unit = {
    try {
      IntellijServer.start()
    } catch {
      case e: Exception =>  Messages.showMessageDialog(e.getMessage, "Could not create nodecg-io server", Messages.getErrorIcon);
    }
  }

  override def appClosing(): Unit = {
    try {
      IntellijServer.stop()
    } catch {
      case e: Exception =>
    }
  }
}
