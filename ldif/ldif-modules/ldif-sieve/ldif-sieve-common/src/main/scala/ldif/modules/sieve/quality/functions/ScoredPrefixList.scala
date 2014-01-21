package ldif.modules.sieve.quality.functions

/*
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

import org.slf4j.LoggerFactory
import ldif.modules.sieve.quality.ScoringFunction

/**
 * Scoring function that assigns real-valued, uniformly distributed scores to a list of graphs.
 * Used to model priority and can be applied to express, for example, reputation.
 *
 * Accepts a list of prefixes. For each incoming graph, will test against each prefix in the list, Could be optimized.
 *
 * Can be used, e.g. for DBpedia: list(http://pt.wikipedia.org, http://en.wikipedia.org,...)
 *
 * @author pablomendes
 */

class ScoredPrefixList(priorityList: List[String]) extends ScoredList(priorityList) {

  private val log = LoggerFactory.getLogger(getClass.getName)

  override def getPosition(graphId: String) : Int = {
    priorityList.filter(graphPrefix => graphId startsWith graphPrefix).headOption match {
      case Some(g) => priorityList.indexOf(g)
      case None => -1
    }
  }

}

object ScoredPrefixList {
  def fromXML(node: scala.xml.Node) : ScoringFunction = {
    try {
      val priorityList = ScoringFunction.getStringConfig(node, "list")
      val params : List[String] = priorityList.split(" ").toList
      if (params.length < 1) {
        throw new IllegalArgumentException("No list of values given as preference")
      }
      new ScoredPrefixList(params)
    }
  }
}





