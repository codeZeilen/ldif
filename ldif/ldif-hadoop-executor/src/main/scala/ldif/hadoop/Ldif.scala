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

import ldif.config.SchedulerConfig
import org.slf4j.LoggerFactory
import java.io.File
import ldif.util.{Consts, CommonUtils, ValidationException, LogUtil}

object Ldif {
  LogUtil.init
  private val log = LoggerFactory.getLogger(getClass.getName)

  def main(args : Array[String])
  {
    var debug = false
    if(args.length==0) {
      log.warn("No configuration file given.")
      printHelpAndExit()
    }
    else if(args.length>=2 && args(0)=="--debug")
      debug = true

    val configFile = if(args.length == 0) {
      val configUrl = getClass.getClassLoader.getResource("ldif/local/neurowiki/scheduler-config.xml")
      new File(configUrl.toString.stripPrefix("file:"))
    } else
      CommonUtils.getFileFromPathOrUrl(args(args.length-1))

    if(!configFile.exists) {
      log.warn("Configuration file not found at "+ configFile.getCanonicalPath)
      printHelpAndExit()
    }
    else {
      // Setup Scheduler
      var config : SchedulerConfig = null
      try {
        config = SchedulerConfig.load(configFile)
      }
      catch {
        case e:ValidationException => {
          log.error("Invalid Scheduler configuration: "+e.toString +
            "\n- More details: " + Consts.xsdScheduler)
          System.exit(1)
        }
      }
      val scheduler = new HadoopScheduler(config, debug)

      // Evaluate jobs at most once. Evaluate import first, then integrate.
      if (scheduler.runOnce) {
        scheduler.evaluateImportJobs
        Thread.sleep(1000)
        while (!scheduler.allJobsCompleted) {
          // wait for jobs to be completed
          Thread.sleep(1000)
        }
        scheduler.evaluateIntegrationJob(false)
        sys.exit(0)
      }
      else {
        log.info("Running LDIF as server")
        // Evaluate jobs every 10 sec, run as server
        while(true){
          scheduler.evaluateJobs
          Thread.sleep(10 * 1000)
        }
      }
    }
  }

  def printHelpAndExit() {
    log.info(Consts.LDIF_HELP_HEADER+
      "\nUsages: ldif-hadoop <schedulerConfiguration>" +
      "\n\tldif-hadoop-integrate <integrationJobConfiguration>" +
      Consts.LDIF_HELP_FOOTER)
    System.exit(1)
  }

}