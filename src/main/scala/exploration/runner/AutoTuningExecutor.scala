package exploration.runner

import arithexpr.arithmetic.RangeMul
import elevate.core.{Failure, RewriteResult, Strategy, Success}
import elevate.heuristic_search.Runner
import elevate.heuristic_search.util.{Solution, SolutionStep, hashProgram}
import rise.autotune.{AutoTuningError, EXECUTION_ERROR, HostCode, Median, Timeouts, Tuner, getBest, getDuration, getSamples, search, tuningParam, wrapOclRun}
import rise.core.Expr
import rise.core.types.Nat
import rise.elevate.Rise
import rise.eqsat.Rewrite
import shine.OpenCL.{GlobalSize, LocalSize}
import util.{Time, TimeSpan}
import elevate.heuristic_search._

import java.io.{File, FileOutputStream, PrintWriter}
import java.nio.file.{Files, Paths}
import scala.collection.immutable
import scala.collection.mutable.ListBuffer

case class AutoTuningExecutor(lowering: Strategy[Rise],
                              goldExpression: Rise,
                              hostCode: Option[HostCode],
                              iterations: Int,
                              inputSize: Int,
                              threshold: Double,
                              output: String
                             ) extends Runner[Rise] {


  case class TuningResultStatistic(
                                    number: Int,
                                    solution: Solution[Rise],
                                    timestamp: Long,
                                    duration: TimeSpan[Time.ms],
                                    durationTuning: TimeSpan[Time.ms],
                                    durationLowering: TimeSpan[Time.ms],
                                    samples: Int,
                                    executions: Int,
                                    runtime: Option[TimeSpan[Time.ms]]
                                  )

  case class Statistics(
                         samples: ListBuffer[TuningResultStatistic]
                       )

  val tuningResults = new ListBuffer[TuningResultStatistic]()
  var number = 0
  val random = new scala.util.Random

  var duration: Long = 0

  def plot(): Unit = {

  }

  override def checkSolution(solution: Solution[Rise]): Boolean = {
    //    val checkStart = System.currentTimeMillis()
    //    val result = exploration.runner.checkSolution(lowering, solution)
    val result = exploration.runner.checkSolutionC(lowering, solution)
    //    duration += System.currentTimeMillis() - checkStart
    //    println("checking duration total: " + duration.toDouble/1000 + " s")
    //    println("checking duration total: " + duration.toDouble/1000/60 + " m")

    result
  }

  def execute2(solution: Solution[Rise]): (Rise, Option[Double]) = {
    number = number + 1

    // throw the dices

    // save statistics
    val loweringDuration = 0
    val tuningDuration = 0
    val totalDuration = 1

    val samples = 1
    val executions = 1

    val index = random.nextInt(100)

    val runtime = index < 50 match {
      case true => Some(index.toDouble)
      case false => None
    }

    saveTuningResults(
      TuningResultStatistic(
        number = number,
        solution = solution,
        timestamp = System.currentTimeMillis(),
        duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
        durationTuning = TimeSpan.inMilliseconds(tuningDuration.toDouble),
        durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
        samples = samples,
        executions = executions,
        runtime = Some(TimeSpan.inMilliseconds(index.toDouble))
      )
    )

    (solution.expression(), runtime)
  }


  // define lowering and gold

  // define executor
  val executor = CExecutor(
    lowering = lowering,
    goldExpression = goldExpression,
    iterations = 3,
    inputSize = 512,
    threshold = 100,
    output = "autotuning",
    saveToDisk = false
  )


  // define execution function
  val executeInternal: Expr => (
    Either[AutoTuningError, Double],
      Option[Double],
      Option[Double],
      Option[Double]
    ) = e => {

    println("execute from here!!")


    val strategies = immutable.Seq.empty[Strategy[Rise]]

    val executionStart = System.currentTimeMillis()

    val sol = Solution[Rise](
      solutionSteps = scala.collection.immutable.Seq(
        SolutionStep[Rise](
          expression = e,
          strategy = null,
          location = -1
        )
      )
    )

    val result = executor.execute(sol).performance


    // todo move to other thing
    val runtime: Either[AutoTuningError, Double] = result match {
      case Some(value) => Right(value)
      case None => Left(AutoTuningError(EXECUTION_ERROR, None))
    }

    // todo measure these properly
    val codegenTime = (System.currentTimeMillis() - executionStart).toDouble
    val compilationTime = (System.currentTimeMillis() - executionStart).toDouble
    val executionTime = (System.currentTimeMillis() - executionStart).toDouble

    (runtime,
      Some(codegenTime),
      Some(compilationTime),
      Some(executionTime))
  }


  def execute(solution: Solution[Rise]): ExplorationResult[Rise] = {
    val totalDurationStart = System.currentTimeMillis()

    number = number + 1

    // each call of execute save following information
    // +1 solution
    // number of samples
    // number of executions
    // duration

    // todo work with gold expression

    println("solution: " + solution)
    println(hashProgram(solution.expression()))


    //    // todo adjust this for autotuning benchmarks
    //    val tuner = Tuner(
    //      hostCode = HostCode("", "", ""), // we don't need that
    //      samples = 100,
    //      name = "rs_emb_1024",
    //      output = "autotuning/tvm_gemm",
    //      timeouts = Timeouts(5000, 5000, 1000),
    //      executionIterations = 10,
    //      speedupFactor = 100,
    //      //      configFile = Some("autotuning/config/mmCPU/rs_cot_1024.json"),
    //      configFile = Some("autotuning/config/mmCPU/rs_emb_1024.json"),
    //      //      configFile = Some("autotuning/config/mmCPU/rs_cot_1024_reorder.json"),
    //      hmConstraints = true,
    //      //      strategyMode = None,
    //      executor = Some(execute),
    //      saveToFile = true
    //    )


    // todo make this hostcode option for tuner

    // create tuner
    val tuner = Tuner(
      hostCode = HostCode("", "", ""), // we don't need that
      samples = iterations,
      name = "mm",
      output = "exploration/",
      timeouts = Timeouts(100000, 100000, 100000), // we might want to adjust this
      executionIterations = 10,
      speedupFactor = threshold,
      runtimeStatistic = Median,
      configFile = None,
      //      Some("/home/jo/development/rise-lang/shine/autotuning/scal/scal.json"),
      hmConstraints = true,
      executor = Some(executeInternal),
      //      hmConstraints = false,
      saveToFile = false
    )

    // lower expression
    val loweringDurationStart = System.currentTimeMillis()
    val lowered = lowering.apply(solution.expression())
    val loweringDuration = System.currentTimeMillis() - loweringDurationStart

    val (result, statistic) = lowered match {
      case Success(p) => {

        //        // now wrap ocl
        //        val eTuning: Expr =
        //          tuningParam("ls0", RangeMul(1, 1024, 2), (ls0: Nat) =>
        //            tuningParam("ls1", RangeMul(1, 1024, 2), (ls1: Nat) =>
        //              tuningParam("gs0", RangeMul(1, 1024, 2), (gs0: Nat) =>
        //                tuningParam("gs1", RangeMul(1, 1024, 2), (gs1: Nat) =>
        //                          wrapOclRun(LocalSize(ls0, ls1), GlobalSize(gs0, gs1))(lowered.get)
        //                ))))

        //        val eTuning: Expr =
        //          tuningParam("i", RangeMul(0, 1, 1), (i: Nat) =>
        //            lowered.get
        //          )

        //        println("eTuning: \n" + eTuning)

        //        val params = rise.autotune.constraints.collectParameters(eTuning)
        //        println("params: ")
        //        params.foreach(println)


        // run tuning
        val tuningDurationStart = System.currentTimeMillis()
        val (runtime, tuningStatistic) = try {
          val result = search(tuner)(lowered.get)

          // meta information
          //          val duration = getDuration(result)
          val samples = getSamples(result)

          println("samples: " + samples)

          val best = getBest(result.samples)
          println("best: " + best)
          println("lowered: " + lowered)

          val runtime = best match {
            case Some(_) =>
              best.get.runtime match {
                case Right(value) => Some(TimeSpan.inMilliseconds(value.value))
                case Left(value) => None
              }
            case None => None
          }

          val tuningDuration = System.currentTimeMillis() - tuningDurationStart
          val totalDuration = System.currentTimeMillis() - totalDurationStart

          (
            runtime,
            TuningResultStatistic(
              number = number,
              solution = solution,
              timestamp = System.currentTimeMillis(),
              duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
              durationTuning = TimeSpan.inMilliseconds(tuningDuration.toDouble),
              durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
              samples = samples,
              executions = tuner.executionIterations * samples,
              runtime
            )
          )
        } catch {
          case e: Throwable =>

            println("tuning is brorken! mey friend")
            println("e: " + e)


            val tuningDuration = System.currentTimeMillis() - tuningDurationStart
            val totalDuration = System.currentTimeMillis() - totalDurationStart

            (
              None,
              TuningResultStatistic(
                number = number,
                solution = solution,
                timestamp = System.currentTimeMillis(),
                duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
                durationTuning = TimeSpan.inMilliseconds(tuningDuration.toDouble),
                durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
                samples = 0,
                executions = 0,
                None
              )
            )
        }

        (
          (solution.expression(), runtime),
          tuningStatistic
        )
      }
      case Failure(s) =>

        // duration lowering
        // measure

        // durationTuning = 0
        val totalDuration = System.currentTimeMillis() - totalDurationStart

        (
          (solution.expression(), None),
          TuningResultStatistic(
            number = number,
            solution = solution,
            timestamp = System.currentTimeMillis(),
            duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
            durationTuning = TimeSpan.inMilliseconds(0.0),
            durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
            samples = 0,
            executions = 0,
            None
          )
        )
    }

    saveTuningResults(statistic)

    // convert from Option[TimeSpan] to Double
    val resultingRuntime = result._2 match {
      case Some(value) => Some(value.value)
      case None => None
    }

    (result._1, resultingRuntime)
    ExplorationResult(
      solution,
      resultingRuntime,
      None
    )
  }

  def execute3(solution: Solution[Rise]): (Rise, Option[Double]) = {
    val totalDurationStart = System.currentTimeMillis()


    number = number + 1

    // each call of execute save following information
    // +1 solution
    // number of samples
    // number of executions
    // duration

    // todo work with gold expression

    println("solution: " + solution)
    println(hashProgram(solution.expression()))

    // create tuner
    val tuner = Tuner(
      //      hostCode = HostCode(init(1024), compute, finish),
      hostCode = hostCode.get,
      inputSizes = Seq(1024, 1024),
      samples = iterations,
      name = "mv",
      output = "exploration/",
      timeouts = Timeouts(100000, 100000, 100000),
      executionIterations = 10,
      runtimeStatistic = Median,
      speedupFactor = threshold,
      None,
      //      Some("/home/jo/development/rise-lang/shine/autotuning/scal/scal.json"),
      hmConstraints = true,
      //      hmConstraints = false,
      saveToFile = false
    )

    // lower expression
    val loweringDurationStart = System.currentTimeMillis()
    val lowered = lowering.apply(solution.expression())
    val loweringDuration = System.currentTimeMillis() - loweringDurationStart

    val (result, statistic) = lowered match {
      case Success(p) => {

        // now wrap ocl
        val eTuning: Expr =
          tuningParam("ls0", RangeMul(1, 1024, 2), (ls0: Nat) =>
            tuningParam("ls1", RangeMul(1, 1024, 2), (ls1: Nat) =>
              tuningParam("gs0", RangeMul(1, 1024, 2), (gs0: Nat) =>
                tuningParam("gs1", RangeMul(1, 1024, 2), (gs1: Nat) =>
                  wrapOclRun(LocalSize(ls0, ls1), GlobalSize(gs0, gs1))(lowered.get)
                ))))

        // run tuning
        val tuningDurationStart = System.currentTimeMillis()
        val (runtime, tuningStatistic) = try {
          val result = search(tuner)(eTuning)

          // meta information
          //          val duration = getDuration(result)
          val samples = getSamples(result)

          println("samples: " + samples)

          val best = getBest(result.samples)
          println("best: " + best)
          println("lowered: " + lowered)

          val runtime = best match {
            case Some(_) =>
              best.get.runtime match {
                case Right(value) => Some(TimeSpan.inMilliseconds(value.value))
                case Left(value) => None
              }
            case None => None
          }

          val tuningDuration = System.currentTimeMillis() - tuningDurationStart
          val totalDuration = System.currentTimeMillis() - totalDurationStart

          (
            runtime,
            TuningResultStatistic(
              number = number,
              solution = solution,
              timestamp = System.currentTimeMillis(),
              duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
              durationTuning = TimeSpan.inMilliseconds(tuningDuration.toDouble),
              durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
              samples = samples,
              executions = tuner.executionIterations * samples,
              runtime
            )
          )
        } catch {
          case e: Throwable =>

            println("tuning is brorken! mey friend")
            println("e: " + e)


            val tuningDuration = System.currentTimeMillis() - tuningDurationStart
            val totalDuration = System.currentTimeMillis() - totalDurationStart

            (
              None,
              TuningResultStatistic(
                number = number,
                solution = solution,
                timestamp = System.currentTimeMillis(),
                duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
                durationTuning = TimeSpan.inMilliseconds(tuningDuration.toDouble),
                durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
                samples = 0,
                executions = 0,
                None
              )
            )
        }

        (
          (solution.expression(), runtime),
          tuningStatistic
        )
      }
      case Failure(s) =>

        // duration lowering
        // measure

        // durationTuning = 0
        val totalDuration = System.currentTimeMillis() - totalDurationStart

        (
          (solution.expression(), None),
          TuningResultStatistic(
            number = number,
            solution = solution,
            timestamp = System.currentTimeMillis(),
            duration = TimeSpan.inMilliseconds(totalDuration.toDouble),
            durationTuning = TimeSpan.inMilliseconds(0.0),
            durationLowering = TimeSpan.inMilliseconds(loweringDuration.toDouble),
            samples = 0,
            executions = 0,
            None
          )
        )
    }

    saveTuningResults(statistic)

    // convert from Option[TimeSpan] to Double
    val resultingRuntime = result._2 match {
      case Some(value) => Some(value.value)
      case None => None
    }

    (result._1, resultingRuntime)
  }

  def saveTuningResults(tuningResultStatistic: TuningResultStatistic) = {

    val filePath = output + "/" + "tuningStatistics.csv"
    val exists = Files.exists(Paths.get(filePath))

    val fWriter = new PrintWriter(new FileOutputStream(new File(filePath), true))

    if (!exists) {
      val header = "number, solution, strategy, timestamp, duration, durationTuning, durationLowering, samples, executions, runtime, runtime2" + "\n"
      fWriter.write(header)
    }

    val runtime = tuningResultStatistic.runtime match {
      case Some(value) => value.toString
      case None => "-1"
    }

    val runtime2 = tuningResultStatistic.runtime match {
      case Some(value) => value.value
      case None => "-1"
    }

    // write line
    val line =
      tuningResultStatistic.number.toString + ", " +
        hashProgram(tuningResultStatistic.solution.expression()) + ", " +
        tuningResultStatistic.solution.strategies().mkString(" : ") + ", " +
        tuningResultStatistic.timestamp.toString + ", " +
        tuningResultStatistic.duration.toString + ", " +
        tuningResultStatistic.durationTuning.toString + ", " +
        tuningResultStatistic.durationLowering.toString + ", " +
        tuningResultStatistic.samples.toString + ", " +
        tuningResultStatistic.executions.toString + ", " +
        runtime.toString + ", " +
        runtime2 + "\n"

    fWriter.write(line)

    fWriter.close()
  }
}
