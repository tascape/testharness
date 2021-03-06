/*
 * Copyright 2015 - 2016 Nebula Bay.
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
package com.tascape.qa.th.test;

import com.tascape.qa.th.data.AbstractTestData;

/**
 *
 * @author linsong wang
 */
public class SampleData extends AbstractTestData {

    public String testParameter = "";

    private static final SampleData[] DATA = new SampleData[]{
        new SampleData() {
            {
                testParameter = "scenario one";
            }

            public String getValue() {
                return "scenario one";
            }
        },
        new SampleData() {
            {
                testParameter = "scenario two";
            }

            public String getValue() {
                return "scenario two";
            }
        },
        new SampleData() {
            {
                testParameter = "scenario three";
                setValue(testParameter);
            }

            public String getValue() {
                return "scenario three";
            }
        }};

    public SampleData[] getData() {
        return DATA;
    }
}
