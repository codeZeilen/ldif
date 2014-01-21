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

package ldif.modules.sieve.quality.functions

import ldif.entity.NodeTrait
import org.slf4j.LoggerFactory
import ldif.modules.sieve.quality.{ScoringFunctionConjunctive, ScoringFunction}

class Threshold(val ts: Int, val min : Boolean = true) extends ScoringFunctionConjunctive {
  private val log = LoggerFactory.getLogger(getClass.getName)
  val max = !min

  def scoreSingleValue(node: NodeTrait): Double = {
    try {
      val indicator: Int = node.value.toInt
      if (min && (indicator >= ts) ||
         (max && (indicator <= ts)))
        1.0
      else
        0.0
    } catch {
      case e: Exception => {
        log.debug("Error %s".format(e))
        0.0
      }
    }
  }

  override def toString(): String = {
    "Threshold, ts= " + ts
  }

  override def equals(obj: Any) = {
    obj match {
      case ots: Threshold => ts == ots.ts
      case _ => false
    }
  }
}

object Threshold {
    def fromXML(node: scala.xml.Node): ScoringFunction = {
        try {
            val ts = ScoringFunction.getIntConfig(node, "min")
            return new Threshold(ts,true)
        } catch {
            case ioe: Exception => try {
                val ts = ScoringFunction.getIntConfig(node, "max")
                return new Threshold(ts,false)
            } catch {
                case ioe: Exception => throw new IllegalArgumentException("Error in threshold provided.")
            }
        }
    }
}