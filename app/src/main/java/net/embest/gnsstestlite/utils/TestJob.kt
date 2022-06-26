/*
 * Copyright (C) 2022 HDGNSS
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

package com.hdgnss.gnsstestlite.utils

class TestJob {
    var name = ""
    var round = 0L
    var delay = 0L
    var cep = ""
    var request = 0L
    var timeout = 0L
    var delete = 0
    var inject_time = false
    var inject_xtra = false
    var count = 1L
        private set

    val finished: Boolean
        get() = count > round

    init {
        this.count = 1L
    }


    fun oneTestDone() {
        this.count = this.count + 1
    }

    fun testDone() {
        this.count = round
    }

    fun newTest() {
        this.count = 1L
    }

    fun cleanJob() {
        this.count = 1L
        this.round = 0L
        this.delay = 0L
        this.name = ""
        this.cep = ""
        this.request = 0L
        this.timeout = 0L
        this.delete = 0
        this.inject_time = false
        this.inject_xtra = false
    }
}