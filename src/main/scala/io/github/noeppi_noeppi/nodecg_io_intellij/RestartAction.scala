package io.github.noeppi_noeppi.nodecg_io_intellij

import java.io.IOException

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.ui.Messages

class RestartAction extends AnAction {

  override def actionPerformed(event: AnActionEvent): Unit = {
    val currentProject = event.getProject
    try {
      IntellijServer.stop()
      IntellijServer.start()
      Messages.showMessageDialog(currentProject, "Server restarted.", "Success", Messages.getInformationIcon)
    } catch {
      case e: IOException =>
        Messages.showMessageDialog(currentProject, "Failed to restart the server: " + e.getMessage, "Failed", Messages.getWarningIcon)
    }
  }

  override def update(e: AnActionEvent): Unit = {
    e.getPresentation.setEnabledAndVisible(true)
  }
}
