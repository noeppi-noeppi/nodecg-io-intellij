package io.github.noeppi_noeppi.nodecg_io_intellij

import java.io._
import java.util.Base64

import com.google.gson._
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationTypeUtil}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.history.LocalHistory
import com.intellij.history.integration.LocalHistoryImpl
import com.intellij.ide.plugins.{PluginManager, PluginManagerCore}
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VFileProperty, VirtualFile, VirtualFileManager}
import com.intellij.tasks.TaskManager
import com.intellij.tasks.impl.LocalTaskImpl
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import io.github.noeppi_noeppi.nodecg_io_intellij.Util.Implicits._
import io.github.noeppi_noeppi.nodecg_io_intellij.request.{Request, Response}

import scala.jdk.javaapi.CollectionConverters

object IntellijHandler extends HttpHandler {

  lazy val handlers: Map[String, Handle] = Map(
    "available_methods" -> availableMethods,
    "get_project" -> getProject,
    "get_open_editor_file" -> getOpenEditorFile,
    "is_project_valid" -> isProjectValid,
    "vfs_exists" -> vfsExists,
    "vfs_delete" -> vfsDelete,
    "vfs_size" -> vfsSize,
    "vfs_writable" -> vfsWritable,
    "vfs_line_sep" -> vfsLineSep,
    "vfs_directory" -> vfsDirectory,
    "vfs_symlink" -> vfsSymlink,
    "vfs_regular" -> vfsRegular,
    "vfs_parent" -> vfsParent,
    "vfs_set_content_text" -> vfsSetContentText,
    "vfs_set_content_bytes" -> vfsSetContentBytes,
    "vfs_get_by_url" -> vfsGetByUrl,
    "vfs_get_content_text" -> vfsGetContentText,
    "vfs_get_content_bytes" -> vfsGetContentBytes,
    "run_get_configuration" -> runGetConfiguration,
    "run_get_configurations" -> runGetConfigurations,
    "run_get_selected" -> runGetSelected,
    "run_get_type" -> runGetType,
    "run_get_types" -> runGetTypes,
    "run_get_type_from_configuration" -> runGetTypeFromConfiguration,
    "run_get_configurations_of_type" -> runGetConfigurationsOfType,
    "run_add_configuration" -> runAddConfiguration,
    "run_remove_configuration" -> runRemoveConfiguration,
    "run_select_configuration" -> runSelectConfiguration,
    "run_get_configuration_name" -> runGetConfigurationName,
    "run_get_configuration_is_template" -> runGetConfigurationIsTemplate,
    "run_start_configuration" -> runStartConfiguration,
    "run_get_type_name" -> runGetTypeName,
    "run_get_type_description" -> runGetTypeDescription,
    "tasks_get" -> tasksGet,
    "tasks_get_all" -> tasksGetAll,
    "tasks_get_active" -> tasksGetActive,
    "tasks_add" -> tasksAdd,
    "tasks_is_open" -> tasksIsOpen,
    "tasks_is_active" -> tasksIsActive,
    "tasks_is_default" -> tasksIsDefault,
    "tasks_close" -> tasksClose,
    "tasks_reopen" -> tasksReopen,
    "tasks_activate" -> tasksActivate,
    "plugin_get" -> pluginGet,
    "plugin_get_all" -> pluginGetAll,
    "plugin_name" -> pluginName,
    "plugin_enabled" -> pluginEnabled,
    "plugin_is_jb" -> pluginIsJetBrains,
    "plugin_get_website" -> pluginGetWebsite,
    "plugin_get_author_name" -> pluginGetAuthorName,
    "plugin_get_author_email" -> pluginGetAuthorEmail,
    "plugin_get_author_website" -> pluginGetAuthorWebsite,
    "plugin_get_description" -> pluginGetDescription,
    "plugin_get_changelog" -> pluginGetChangelog,
    "plugin_get_version" -> pluginGetVersion,
    "lh_find_label" -> lhFindLabel,
    "lh_get_labels" ->lhGetLabels,
    "lh_get_changes" -> lhGetChanges,
    "lh_add_label" -> lhAddLabel,
    "lh_change_affects" -> lhChangeAffects,
    "lh_change_revert" -> lhChangeRevert,
    "lh_change_timestamp" -> lhChangeTimestamp,
    "lh_change_text" -> lhChangeText,
    "lh_change_bytes" -> lhChangeBytes,
    "lh_label_name" -> lhLabelName,
    "lh_label_color" -> lhLabelColor,
    "run_action" -> runAction
  ).map(entry => (entry._1.toLowerCase(), entry._2))

  override def handle(exchange: HttpExchange): Unit = {

    def sendResponse(response: Response)(implicit gson: Gson): Unit = {
      try {
        exchange.getResponseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, 0)
        val out = new OutputStreamWriter(exchange.getResponseBody)
        out.write(gson.toJson(response) + "\n")
        out.close()
        exchange.close()
        println(gson.toJson(response))
      } catch {
        case e: Exception =>
          System.err.println("Could not send response: " + e.getMessage)
          e.printStackTrace()
      }
    }

    try {
      val req = try {
        gson.fromJson(new InputStreamReader(exchange.getRequestBody), classOf[Request])
      } catch {
        case e: JsonSyntaxException =>
          val data = new JsonObject
          data.addProperty("error_msg", e.getMessage)
          sendResponse(new Response(false, "invalid json", data))
          return
        case e: JsonIOException =>
          val data = new JsonObject
          data.addProperty("error_msg", e.getMessage)
          sendResponse(new Response(false, "invalid request", data))
          return
        case e: JsonParseException =>
          val data = new JsonObject
          data.addProperty("error_msg", e.getMessage)
          sendResponse(new Response(false, "could not read json", data))
          return
      }

      val reply = try {
        handlers.getOrElse(req.method.toLowerCase(), unknown).handle(req.data)
      } catch {
        case e: NullPointerException =>
          val data = new JsonObject
          data.addProperty("error_msg", "NPE: Probably invalid JSON: " + e.getMessage)
          new Response(false, "NullPointerException", data)
        case e: Exception =>
          e.printStackTrace()
          val data = new JsonObject
          data.addProperty("error_msg", e.getMessage)
          new Response(false, e.getClass.getSimpleName, data)
      }

      sendResponse(reply)

    } catch {
      case e: IOException =>
        val data = new JsonObject
        data.addProperty("error_msg", e.getMessage)
        sendResponse(new Response(false, "generic error", data))
      case e: Exception =>
        val data = new JsonObject
        data.addProperty("error_msg", e.getMessage)
        sendResponse(new Response(false, "unexpected error", data))
    }
  }

  sealed trait Handle {
    def handle(data: JsonObject): Response

    protected final def project(data: JsonObject): Project = {
      data.get("project").optionString match {
        case Some(proj) =>
          val project = Util.getNamedProject(proj)
          if (project == null) {
            throw new IllegalStateException("Project not found.")
          } else {
            project
          }
        case None => throw new IllegalStateException("No project given.")
      }
    }

    protected final def file(data: JsonObject): VirtualFile = {
      data.get("vfs_file").optionString match {
        case Some(url) =>
          val vfs_file = VirtualFileManager.getInstance().findFileByUrl(url)
          if (vfs_file == null) {
            throw new IllegalStateException("File not found.")
          } else {
            vfs_file
          }
        case None => throw new IllegalStateException("No vfs_file given.")
      }
    }

    protected final def nullableProject(data: JsonObject): Project = {
      if (!data.has("project"))
        return null
      data.get("project").optionString match {
        case Some(proj) =>
          val project = Util.getNamedProject(proj)
          if (project == null) {
            null
          } else {
            project
          }
        case None => null
      }
    }

    protected final def nullableFile(data: JsonObject): VirtualFile = {
      if (!data.has("vfs_file"))
        return null
      data.get("vfs_file").optionString match {
        case Some(url) => VirtualFileManager.getInstance().findFileByUrl(url)
        case None => null
      }
    }
  }

  object unknown extends Handle {
    override def handle(data: JsonObject): Response = new Response(false, "Unknown method", new JsonObject)
  }

  object availableMethods extends Handle {
    override def handle(data: JsonObject): Response = {
      val data = new JsonArray(handlers.size)
      for (handler <- handlers.keys)
        data.add(handler)
      new Response(true, "ok", data)
    }
  }

  object getProject extends Handle {
    override def handle(data: JsonObject): Response = {
      val project = data.get("project").optionString match {
        case Some(projectName) => Util.getNamedProject(projectName)
        case None => Util.getActiveProject
      }
      if (project == null) {
        new Response(true, "ok", JsonNull.INSTANCE)
      } else {
        new Response(true, "ok", project.getName)
      }
    }
  }

  object isProjectValid extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", nullableProject(data) != null)
  }

  object getOpenEditorFile extends Handle {
    override def handle(data: JsonObject): Response = {
      val proj = project(data)
      val openFile = Util.getFirstOpenFile(proj)
      if (openFile == null) {
        new Response(true, "ok", JsonNull.INSTANCE)
      } else {
        new Response(true, "ok", openFile.getUrl)
      }
    }
  }

  object vfsExists extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", { val vf = nullableFile(data); vf != null && vf.exists() })
  }

  object vfsDelete extends Handle {
    override def handle(data: JsonObject): Response = { file(data).delete(this); new Response(true, "ok") }
  }

  object vfsSize extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).getLength)
  }

  object vfsWritable extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).isWritable)
  }

  object vfsLineSep extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).getDetectedLineSeparator)
  }

  object vfsDirectory extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).isDirectory)
  }

  object vfsSymlink extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).is(VFileProperty.SYMLINK))
  }

  object vfsRegular extends Handle {
    override def handle(data: JsonObject): Response = { val vf = file(data); new Response(true, "ok", !vf.isDirectory && !vf.is(VFileProperty.SPECIAL)) }
  }

  object vfsParent extends Handle {
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).getParent.getUrl)
  }

  object vfsSetContentText extends Handle {
    override def handle(data: JsonObject): Response = {
      val vf = file(data)
      val lsep = if (vf.getDetectedLineSeparator == null) System.lineSeparator() else vf.getDetectedLineSeparator
      val content = data.get("content").optionString.getOrElse("").replace("\r\n", "\n").replace("\n", lsep)
      ApplicationManager.getApplication.invokeAndWait(() => {
        val out = new OutputStreamWriter(vf.getOutputStream(this))
        out.write(content)
        out.flush()
        out.close()
      })
      new Response(true, "ok")
    }
  }

  object vfsSetContentBytes extends Handle {
    override def handle(data: JsonObject): Response = {
      val vf = file(data)
      val content: Array[Byte] = data.get("content").optionString.map(Base64.getDecoder.decode).getOrElse(Array[Byte]())
      ApplicationManager.getApplication.invokeAndWait(() => {
        val out = vf.getOutputStream(this)
        out.write(content)
        out.flush()
        out.close()
      })
      new Response(true, "ok")
    }
  }

  object vfsGetByUrl extends Handle {
    override def handle(data: JsonObject): Response = {
      val vf = VirtualFileManager.getInstance().findFileByUrl(data.get("url").getAsString)
      if (vf == null) {
        new Response(true, "ok", JsonNull.INSTANCE)
      } else {
        new Response(true, "ok", vf.getUrl)
      }
    }
  }

  object vfsGetContentText extends Handle {
    override def handle(data: JsonObject): Response = {
      val in = new BufferedReader(new InputStreamReader(file(data).getInputStream))
      val sb = new StringBuilder
      in.lines().forEach(line => {sb.append(line); sb.append("\n")})
      in.close()
      new Response(true, "ok", sb.toString())
    }
  }

  object vfsGetContentBytes extends Handle {
    override def handle(data: JsonObject): Response = {
      val in = file(data).getInputStream
      val content = in.readAllBytes()
      in.close()
      new Response(true, "ok", Base64.getEncoder.encodeToString(content))
    }
  }

  object runGetConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      new Response(true, "ok", rm.findConfigurationByName(data.get("configuration").getAsString).getUniqueID)
    }
  }

  object runGetConfigurations extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val ja = new JsonArray()
      rm.getAllSettings.forEach(configuration => ja.add(configuration.getUniqueID))
      new Response(true, "ok", ja)
    }
  }

  object runGetSelected extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val rc = rm.getSelectedConfiguration
      if (rc == null) {
        new Response(true, "ok", JsonNull.INSTANCE)
      } else {
        new Response(true, "ok", rc.getUniqueID)
      }
    }
  }

  object runGetType extends Handle {
    override def handle(data: JsonObject): Response = {
      val ctype = ConfigurationTypeUtil.findConfigurationType(data.get("type").getAsString)
      if (ctype == null)
        throw new IllegalStateException("Type not found")
      new Response(true, "ok", ctype.getId)
    }
  }

  object runGetTypes extends Handle {
    override def handle(data: JsonObject): Response = {
      val ja = new JsonArray()
      ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList.forEach(ctype => ja.add(ctype.getId))
      new Response(true, "ok", ja)
    }
  }

  object runGetTypeFromConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val ctype = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString).getType
      new Response(true, "ok", ctype.getId)
    }
  }

  object runGetConfigurationsOfType extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val ctype = ConfigurationTypeUtil.findConfigurationType(data.get("type").getAsString)
      if (ctype == null)
        throw new IllegalStateException("Type not found")
      val ja = new JsonArray()
      rm.getConfigurationSettingsList(ctype).forEach(configuration => ja.add(configuration.getUniqueID))
      new Response(true, "ok", ja)
    }
  }

  object runAddConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val ctype = ConfigurationTypeUtil.findConfigurationType(data.get("type").getAsString)
      val name = data.get("name").getAsString
      val newConfiguration = rm.createConfiguration(name, ctype.getConfigurationFactories.head)
      rm.addConfiguration(newConfiguration)
      new Response(true, "ok", newConfiguration.getUniqueID)
    }
  }

  object runRemoveConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val configuration = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString)
      rm.removeConfiguration(configuration)
      new Response(true, "ok")
    }
  }

  object runSelectConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val configuration = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString)
      rm.setSelectedConfiguration(configuration)
      new Response(true, "ok")
    }
  }

  object runGetConfigurationName extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val configuration = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString)
      new Response(true, "ok", configuration.getName)
    }
  }

  object runGetConfigurationIsTemplate extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val configuration = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString)
      new Response(true, "ok", configuration.isTemplate)
    }
  }

  object runStartConfiguration extends Handle {
    override def handle(data: JsonObject): Response = {
      val rm = RunManager.getInstance(project(data))
      val configuration = Util.ensureRunConfiguration(rm, data.get("configuration").getAsString)
      ExecutionUtil.runConfiguration(configuration, DefaultRunExecutor.getRunExecutorInstance)
      new Response(true, "ok")
    }
  }

  object runGetTypeName extends Handle {
    override def handle(data: JsonObject): Response = {
      val ctype = ConfigurationTypeUtil.findConfigurationType(data.get("type").getAsString)
      if (ctype == null)
        throw new IllegalStateException("Type not found")
      new Response(true, "ok", ctype.getDisplayName)
    }
  }

  object runGetTypeDescription extends Handle {
    override def handle(data: JsonObject): Response = {
      val ctype = ConfigurationTypeUtil.findConfigurationType(data.get("type").getAsString)
      if (ctype == null)
        throw new IllegalStateException("Type not found")
      new Response(true, "ok", ctype.getConfigurationTypeDescription)
    }
  }

  object tasksGet extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).map(task => task.getId).orNull
      new Response(true, "ok", task)
    }
  }

  object tasksGetAll extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val includeCompleted = data.get("include_completed").getAsBoolean
      val ja = new JsonArray()
      tm.getLocalTasks(includeCompleted).forEach(task => ja.add(task.getId))
      new Response(true, "ok", ja)
    }
  }

  object tasksGetActive extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      new Response(true, "ok", tm.getActiveTask.getId)
    }
  }

  object tasksAdd extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskSummary = data.get("summary").getAsString
      val task = tm.createLocalTask(taskSummary)
      tm.addTask(task)
      new Response(true, "ok", task.getId)
    }
  }

  object tasksIsOpen extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      if (task == null) {
        throw new IllegalStateException("Task not found")
      }
      new Response(true, "ok", !task.isClosed)
    }
  }

  object tasksIsActive extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      if (task == null) {
        throw new IllegalStateException("Task not found")
      }
      new Response(true, "ok", task.isActive)
    }
  }

  object tasksIsDefault extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      if (task == null) {
        throw new IllegalStateException("Task not found")
      }
      new Response(true, "ok", task.isDefault)
    }
  }

  object tasksClose extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      task match {
        case null => throw new IllegalStateException("Task not found")
        case x: LocalTaskImpl => x.setClosed(true)
        case _ => throw new IllegalStateException("Task not local. Can't be closed.")
      }
      task.isClosed
      new Response(true, "ok")
    }
  }

  object tasksReopen extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      task match {
        case null => throw new IllegalStateException("Task not found")
        case x: LocalTaskImpl => x.setClosed(false)
        case _ => throw new IllegalStateException("Task not local. Can't be reopened.")
      }
      task.isClosed
      new Response(true, "ok")
    }
  }

  object tasksActivate extends Handle {
    override def handle(data: JsonObject): Response = {
      val tm = TaskManager.getManager(project(data))
      val taskId = data.get("task").getAsString
      val task = CollectionConverters.asScala(tm.getLocalTasks).find(task => task.getId == taskId).orNull
      if (task == null) {
        throw new IllegalStateException("Task not found")
      }
      val clearContext = data.get("clear_context").getAsBoolean
      ApplicationManager.getApplication.invokeAndWait(() => {
        tm.activateTask(task, clearContext)
      })
      new Response(true, "ok")
    }
  }

  object pluginGet extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null) {
        new Response(true, "ok", JsonNull.INSTANCE)
      } else {
        new Response(true, "ok", plugin.getPluginId.getIdString)
      }
    }
  }

  object pluginGetAll extends Handle {
    override def handle(data: JsonObject): Response = {
      val includeDisabled = data.get("include_disabled").getAsBoolean
      val ja = new JsonArray()
      PluginManagerCore.getPlugins.filter(plugin => includeDisabled || plugin.isEnabled).foreach(plugin => ja.add(plugin.getPluginId.getIdString))
      new Response(true, "ok", ja)
    }
  }

  object pluginName extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getName)
    }
  }

  object pluginEnabled extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.isEnabled)
    }
  }

  object pluginIsJetBrains extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", PluginManager.getInstance().isDevelopedByJetBrains(plugin) || plugin.getPluginId.getIdString == "com.intellij")
    }
  }

  object pluginGetWebsite extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getUrl)
    }
  }

  object pluginGetAuthorName extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getVendor)
    }
  }

  object pluginGetAuthorEmail extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getVendorEmail)
    }
  }

  object pluginGetAuthorWebsite extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getVendorUrl)
    }
  }

  object pluginGetDescription extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getDescription)
    }
  }

  object pluginGetChangelog extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getChangeNotes)
    }
  }

  object pluginGetVersion extends Handle {
    override def handle(data: JsonObject): Response = {
      val id = data.get("plugin_id").getAsString
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(id))
      if (plugin == null)
        throw new IllegalStateException("Plugin not found")
      new Response(true, "ok", plugin.getVersion)
    }
  }

  object lhFindLabel extends Handle {
    override def handle(data: JsonObject): Response = {
      val label = data.get("label").getAsString
      new Response(true, "ok", Util.getLhLabels.find(change => change.getLabel == label).map(change => change.getId).get)
    }
  }

  object lhGetLabels extends Handle {
    override def handle(data: JsonObject): Response = {
      val ja = new JsonArray()
      Util.getLhLabels.foreach(change => ja.add(change.getId))
      new Response(true, "ok", ja)
    }
  }

  object lhGetChanges extends Handle {
    override def handle(data: JsonObject): Response = {
      val vf = nullableFile(data)
      val ja = new JsonArray()
      Util.getLhChanges(vf).foreach(change => ja.add(change.getId))
      new Response(true, "ok", ja)
    }
  }

  object lhAddLabel extends Handle {
    override def handle(data: JsonObject): Response = {
      val label = data.get("label").getAsString
      val color = data.get("color").getAsInt
      if (color < 0) {
        LocalHistory.getInstance().putSystemLabel(project(data), label)
      } else {
        LocalHistory.getInstance().putSystemLabel(project(data), label, color)
      }
      new Response(true, "ok", Util.getLhLabels.find(l => l.getLabel == label).get.getId)
    }
  }

  object lhChangeAffects extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = data.get("change_id").getAsLong
      val vf = file(data)
      new Response(true, "ok", Util.ensureLhChange(change).affectsPath(vf.getPath))
    }
  }

  object lhChangeRevert extends Handle {
    override def handle(data: JsonObject): Response = {
      val proj = project(data)
      val vf = nullableFile(data)
      val change = Util.ensureLhChange(data.get("change_id").getAsLong)
      val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
      if (vf == null) {
        val changesUpTo = Util.getLhChanges(null).filter(c => c.getTimestamp >= change.getTimestamp)
        changesUpTo.foreach(c => {
          c.getAffectedPaths.forEach(path => {
            val realFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, path))
            if (realFile != null)
              Util.revertChangeSet(proj, realFile, lh, c)
          })
        })
      } else {
        Util.revertChangeSet(proj, vf, lh, change)
      }
      new Response(true, "ok")
    }
  }

  object lhChangeTimestamp extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = data.get("change_id").getAsLong
      new Response(true, "ok", Util.ensureLhChange(change).getTimestamp)
    }
  }

  object lhChangeBytes extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = data.get("change_id").getAsLong
      val vf = file(data)
      val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
      val bytes = Util.getBytes(vf, lh, Util.ensureLhChange(change))
      new Response(true, "ok", Base64.getEncoder.encodeToString(bytes))
    }
  }

  object lhChangeText extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = data.get("change_id").getAsLong
      val vf = file(data)
      val lh = LocalHistory.getInstance().asInstanceOf[LocalHistoryImpl]
      val bytes = Util.getBytes(vf, lh, Util.ensureLhChange(change))
      if (bytes == null)
        throw new IllegalStateException("The change does not affect the file")
      val in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes)))
      val sb = new StringBuilder
      in.lines().forEach(line => {sb.append(line); sb.append("\n")})
      in.close()
      new Response(true, "ok", sb.toString())
    }
  }

  object lhLabelName extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = Util.ensureLhChange(data.get("change_id").getAsLong)
      if (change.getLabel == null)
        throw new IllegalStateException("Not a label")
      new Response(true, "ok", change.getLabel)
    }
  }

  object lhLabelColor extends Handle {
    override def handle(data: JsonObject): Response = {
      val change = Util.ensureLhChange(data.get("change_id").getAsLong)
      if (change.getLabel == null)
        throw new IllegalStateException("Not a label")
      new Response(true, "ok", change.getLabelColor)
    }
  }

  object runAction extends Handle {
    override def handle(data: JsonObject): Response = {
      val action = ActionManager.getInstance().getAction(data.get("action").getAsString)
      val place = data.get("place").getAsString
      val sync = data.get("sync").getAsBoolean
      val proj = nullableProject(data)
      if (action == null)
        throw new IllegalStateException("Action not found")
      Util.runAction(action, place, sync, proj)
      new Response(true, "ok")
    }
  }
}
