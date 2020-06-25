package io.github.noeppi_noeppi.nodecg_io_intellij

import java.io.{BufferedReader, IOException, InputStreamReader, OutputStreamWriter}
import java.util.Base64

import com.google.gson._
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationTypeUtil}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VFileProperty, VirtualFile, VirtualFileManager}
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import io.github.noeppi_noeppi.nodecg_io_intellij.Util.Implicits._
import io.github.noeppi_noeppi.nodecg_io_intellij.request.{Request, Response}

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
    "run_get_configuration_name" -> runGetConfigurationName,
    "run_get_configuration_is_template" -> runGetConfigurationIsTemplate,
    "run_start_configuration" -> runStartConfiguration,
    "run_get_type_name" -> runGetTypeName,
    "run_get_type_description" -> runGetTypeDescription
  ).map(entry => (entry._1.toLowerCase(), entry._2))

  override def handle(exchange: HttpExchange): Unit = {

    def sendResponse(response: Response)(implicit gson: Gson): Unit = {
      exchange.getResponseHeaders.add("Content-Type", "application/json")
      exchange.sendResponseHeaders(200, 0)
      val out = new OutputStreamWriter(exchange.getResponseBody)
      out.write(gson.toJson(response) + "\n")
      out.close()
      exchange.close()
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
        case e: Exception => new Response(false, e.getMessage, new JsonObject)
      }

      sendResponse(reply)

    } catch {
      case e: IOException =>
        val data = new JsonObject
        data.addProperty("error_msg", e.getMessage)
        sendResponse(new Response(false, "generic error", data))
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
    override def handle(data: JsonObject): Response = new Response(true, "ok", project(data) != null)
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
    override def handle(data: JsonObject): Response = new Response(true, "ok", file(data).exists())
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
      val out = new OutputStreamWriter(vf.getOutputStream(this))
      out.write(content)
      out.close()
      new Response(true, "ok")
    }
  }

  object vfsSetContentBytes extends Handle {
    override def handle(data: JsonObject): Response = {
      val content: Array[Byte] = data.get("content").optionString.map(Base64.getDecoder.decode).getOrElse(Array[Byte]())
      val out = file(data).getOutputStream(this)
      out.write(content)
      out.close()
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
}
