/* 
 * Copyright 2011-2012 Freie Universität Berlin, MediaEvent Services GmbH & Co. KG
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

package ldif.hadoop.io

import ldif.hadoop.types.SameAsPairWritable
import org.apache.hadoop.io.NullWritable
import org.apache.hadoop.mapred.{SequenceFileOutputFormat, SequenceFileInputFormat}

/**
 * Created by IntelliJ IDEA.
 * User: andreas
 * Date: 11/29/11
 * Time: 11:16 AM
 * To change this template use File | Settings | File Templates.
 */

class SameAsPairSequenceFileInputFormat extends SequenceFileInputFormat[NullWritable, SameAsPairWritable]

class SameAsPairSequenceFileOutputFormat extends SequenceFileOutputFormat[NullWritable, SameAsPairWritable]