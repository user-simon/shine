package shine.OpenCL

case class Module(host: shine.C.Module, kernels: Seq[KernelModule]) {
  def compose(other: Module): Module =
    Module(
      host compose other.host,
      kernels ++ other.kernels)
}

object Module {
  def compose(ms: Seq[Module]): Module = ms.reduce(_ compose _)

  private def kernelSource(km: KernelModule): String = {
    // assumes a single kernel per module
    val name = km.kernels(0).code.name
    val code = util.gen.opencl.kernel.asString(km)
    s"""const char ${name}_source[] =
       |"${code.linesIterator.toArray.mkString(s"${'"'}\n${'"'}")}";
       |""".stripMargin
  }

  def translateToString(m: Module): String =
    s"""
       |${m.kernels.map(kernelSource).mkString("\n")}
       |#define loadKernel(ctx, id)\\
       |  loadKernelFromSource(ctx, #id, id##_source, sizeof(id##_source) - 1)
       |${util.gen.c.function.asString(m.host)}
       |""".stripMargin

  def dumpToDirectory(dir: java.io.File)(m: Module): Unit = {
    util.writeToPath(s"${dir.getAbsolutePath}/host.c",
      s"""#define loadKernel(ctx, ident) loadKernelFromFile(ctx, #ident, #ident ".cl")
         |util.gen.c.function.asString(host)
         |""".stripMargin)
    m.kernels.foreach { km =>
      // assumes a single kernel per module
      val fileName = km.kernels(0).code.name
      util.writeToPath(s"${dir.getAbsolutePath}/$fileName.cl",
        util.gen.opencl.kernel.asString(km))
    }
  }
}
