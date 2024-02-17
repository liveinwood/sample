import zio._
import zio.Console.*
import zio.stream.*
import zio.prelude.Validation
import zio.prelude.ZValidation.Success
import zio.prelude.ZValidation.Failure
import java.nio.file.{Paths, Files}

object MyApp extends ZIOAppDefault:
  def run = Ranking.run("entry.csv", "log.csv")

object Ranking:

  def makeEntryMap(file: String) =
    val sink = ZSink.foldLeft(Map.empty[String, String]) {
      (m: Map[String, String], e: Entry) => m + (e.playerId -> e.handleName)
    }

    ZStream
      .fromIteratorScoped(
        ZIO
          .fromAutoCloseable(
            ZIO.attempt(scala.io.Source.fromFile(file))
          )
          .map(_.getLines())
      )
      .drop(1)
      .map(Entry.validateEntry(_).toEither)
      .absolve
      .run(sink)

  def makeRanking(logFile: String, entryMap: Map[String, String]) =
    val sink = ZSink.foldLeft(Map.empty[String, Int]) {
      (m: Map[String, Int], log: PlayLog) =>
        if (m.getOrElse(log.playerId, 0) >= log.score) m
        else m + (log.playerId -> log.score)
    }

    ZStream
      .fromIteratorScoped(
        ZIO
          .fromAutoCloseable(
            ZIO.attempt(scala.io.Source.fromFile(logFile))
          )
          .map(_.getLines())
      )
      .drop(1)
      .map(PlayLog.validatePlayLog(_).toEither)
      .absolve
      .filter(log => entryMap.contains(log.playerId))
      .run(sink)

  def sortRanking(
      ranking: Map[String, Int],
      entryMap: Map[String, String]
  ): List[(Int, String, String, Int)] = {
    val sortedRank = ranking.toList.sortWith { (r1, r2) =>
      if (r1._2 > r2._2) {
        true
      } else if (r1._2 < r2._2) {
        false
      } else {
        r1._1 < r2._1
      }
    }

    def go(
        entryMap: Map[String, String],
        sortedRank: List[(String, Int)],
        rankNo: Int,
        playerCount: Int,
        accum: List[(Int, String, String, Int)]
    ): List[(Int, String, String, Int)] = {
      if (sortedRank.isEmpty || rankNo > 10 || playerCount > 10) {
        accum
      } else {
        val (_, score) = sortedRank.head
        val (head, rest) = sortedRank.span((_, s) => s == score)

        val accum2 = accum ++ head.map((pid, score) =>
          (rankNo, pid, entryMap.getOrElse(pid, ""), score)
        )

        val rankNo2 = rankNo + head.length
        val playerCount2 = playerCount + head.length

        go(entryMap, rest, rankNo2, playerCount2, accum2)
      }
    }

    go(entryMap, sortedRank, 1, 0, Nil)
  }

  def printRanking(sortedRank: List[(Int, String, String, Int)]) =
    for {
      _ <- printLine("rank,player_id,handle_name,score")
      _ <- ZIO.foreachDiscard(sortedRank) { rank =>
        printLine(
          rank._1.toString() + "," + rank._2 + "," + rank._3 + "," + rank._4
            .toString()
        )
      }
    } yield ()

  def run(entryFile: String, logFile: String) =
    (for {
      entryFile <-
        if (Files.exists(Paths.get(entryFile))) ZIO.succeed(entryFile)
        else ZIO.fail("file.txt not found")
      logFile <-
        if (Files.exists(Paths.get(logFile))) ZIO.succeed(logFile)
        else ZIO.fail("log.txt not found")
      entryMap <- makeEntryMap(entryFile)
      ranking <- makeRanking(logFile, entryMap)
      sortedRank = sortRanking(ranking, entryMap)
      _ <- printRanking(sortedRank)
    } yield ()).foldZIO(
      e => printLineError(s"ERROR! ${e}") *> ZIO.succeed(ExitCode(1)),
      m => printLine("OK") *> ZIO.succeed(ExitCode(0))
    )

  def validatePlayerId(id: String): Validation[String, String] =
    if ("^[0-9a-zA-Z_]{1,20}$".r.matches(id)) Validation.succeed(id)
    else Validation.fail(s"ERROR! invalid player id ${id}}")

  def validateHandleName(name: String): Validation[String, String] =
    if ("^[0-9a-zA-Z_]{1,20}$".r.matches(name)) Validation.succeed(name)
    else Validation.fail(s"ERROR! invalid handle name ${name}}")

  def validateTimestamp(s: String): Validation[String, String] =
    if ("""^\d{4}\-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$""".r.matches(s) || true)
      Validation.succeed(s)
    else Validation.fail(s"ERROR! invalid timestamp ${s}")

  def validateScore(s: String): Validation[String, Int] =
    if ("""^\d+$""".r.matches(s)) Validation.succeed(s.toInt)
    else Validation.fail(s"invalid score ${s}")

  case class Entry(playerId: String, handleName: String)

  object Entry:
    def validateEntry(line: String): Validation[String, Entry] =
      line.split(",") match {
        case Array(id, name) =>
          Validation.validateWith(
            validatePlayerId(id),
            validateHandleName(name)
          )(
            Entry.apply
          )
        case _ => Validation.fail(s"ERROR! invalid line ${line}")
      }

  case class PlayLog(createTimestamp: String, playerId: String, score: Int)

  object PlayLog:
    def validatePlayLog(line: String): Validation[String, PlayLog] =
      line.split(",") match {
        case Array(time, id, score) =>
          Validation.validateWith(
            validateTimestamp(time),
            validatePlayerId(id),
            validateScore(score)
          )(
            PlayLog.apply
          )
        case _ => Validation.fail(s"ERROR! invalid line ${line}")
      }

  case class RankEntry(
      rank: Int,
      playerId: String,
      handleName: String,
      score: Int
  )
