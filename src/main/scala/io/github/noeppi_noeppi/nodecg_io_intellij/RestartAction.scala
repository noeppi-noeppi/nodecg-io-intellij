package io.github.noeppi_noeppi.nodecg_io_intellij

import java.io.IOException

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.Messages

class RestartAction extends AnAction {

  override def actionPerformed(event: AnActionEvent): Unit = {
    try {
      IntellijServer.stop()
      IntellijServer.start()
    } catch {
      case e: IOException =>
        val currentProject = event.getProject
        Messages.showMessageDialog(currentProject, "Failed to restart the server: " + e.getMessage, "Failed", Messages.getWarningIcon)
    }
  }

  override def update(e: AnActionEvent): Unit = {
    val project = e.getProject
    e.getPresentation.setEnabledAndVisible(true)
  }
}
