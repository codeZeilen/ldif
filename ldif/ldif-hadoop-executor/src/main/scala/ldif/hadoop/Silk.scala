/*
 * LDIF
 *
 * Copyright 2011-2014 Universität Mannheim, MediaEvent Services GmbH & Co. KG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ldif.hadoop

import io.EntityMultipleSequenceFileOutput
import java.io.File

import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import ldif.modules.silk.SilkModule
import ldif.modules.silk.hadoop.SilkHadoopExecutor
import de.fuberlin.wiwiss.silk.util.DPair._
import de.fuberlin.wiwiss.silk.util.DPair
import runtime.{HadoopQuadToTextConverter, StaticEntityFormat}

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 3/26/12
 * Time: 1:32 PM
 * To change this template use File | Settings | File Templates.
 */

object Silk {
  def execute(args: Array[String]) {
    if(args.length < 3) {
      sys.error("Parameters: <Silk link spec> <input path> <output path>")
      sys.exit(1)
    }

    val silkModule = SilkModule.load(new File(args(0)))
    val input = args(1)
    val tempEbDir = "tmp_eb_output"+System.currentTimeMillis
    val tempSilkDir = "tmp_silk_output"+System.currentTimeMillis
    val output = args(2)

    // remove existing output
    clean(output)

    val silkExecutor = new SilkHadoopExecutor
    val tasks = silkModule.tasks.toIndexedSeq
    val entityDescriptions = tasks.map(silkExecutor.input).flatMap{ case StaticEntityFormat(ed) => ed }

    val start = System.currentTimeMillis
    EB.buildEntities(input, tempEbDir, entityDescriptions)
    println("Time needed to build entities for linking phase: " + (System.currentTimeMillis()-start)/1000.0 + "s")

    val resultPaths = for((silkTask, i) <- tasks.zipWithIndex) yield {
      val sourcePath = new Path(tempEbDir, EntityMultipleSequenceFileOutput.generateDirectoryName(i * 2))
      val targetPath = new Path(tempEbDir, EntityMultipleSequenceFileOutput.generateDirectoryName(i * 2 + 1))
      val outputPath = new Path(tempSilkDir, EntityMultipleSequenceFileOutput.generateDirectoryName(i))

      silkExecutor.execute(silkTask, DPair(sourcePath, targetPath), outputPath)

      outputPath
    }
    val hdfs = FileSystem.get(new Configuration())
    HadoopQuadToTextConverter.execute(resultPaths.filter(p => hdfs.exists(p)).map(_.toString), output)
    clean(tempSilkDir)
    clean(tempEbDir)
  }

  // Delete path/directory
  private def clean(hdPath: String) : Path =  {
    val path = new Path(hdPath)
    val hdfs = FileSystem.get(new Configuration())
    if (hdfs.exists(path))
      hdfs.delete(path, true)
    path
  }
}