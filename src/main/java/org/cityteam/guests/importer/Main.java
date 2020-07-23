/*
 * Copyright 2020 CityTeam, craigmcc.
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
package org.cityteam.guests.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

import static org.cityteam.guests.client.AbstractClient.PROPERTY_BASE_URI;

public class Main {

    // Accept mm/dd/yy or mm/dd/yyyy
    public static DateTimeFormatter alternateDateTimeFormatter = createDateTimeFormatter();
    public static LocalDate fromDate;
    public static LocalDate toDate;
    public static BufferedReader reader;
    public static Scanner scanner;
    //    public static final RegistrationClient registrationClient = new RegistrationClient();

    // Main Program ----------------------------------------------------------

    public static void main(String[] args) {

        // Parse command line arguments
        if (args.length != 3) {
            System.out.println("Three arguments required:  fromDate, toDate, pathToCSVFle");
            System.out.println("In addition, server URI is passed as system property " +
                    PROPERTY_BASE_URI);
            System.exit(2);
        }
        try {
            fromDate = parseDate(args[0]);
        } catch (DateTimeParseException e) {
            System.out.println("Parsing fromDate '" + args[0] + "' failed");
            System.exit(2);
        }
        try {
            toDate = parseDate(args[1]);
        } catch (DateTimeParseException e) {
            System.out.println("Parsing toDate '" + args[0] + "' failed");
            System.exit(3);
        }
        String pathname = args[2];
        System.out.println("fromDate= " + fromDate.toString());
        System.out.println("toDate=   " + toDate.toString());
        System.out.println("pathname= " + pathname);
        URI serverURI = null;
        try {
            serverURI = new URI(System.getProperty(PROPERTY_BASE_URI));
        } catch (URISyntaxException e) {
            System.out.println("Error parsing serverURI '" + args[2] + "': " + e.getMessage());
            System.exit(4);
        }
        System.out.println("serverUri=" + serverURI);

        // Set up reader
        try {
            reader = new BufferedReader(new FileReader(pathname));
        } catch (FileNotFoundException e) {
            System.out.println("Pathname '" + pathname + "' cannot be found");
            System.exit(5);
        }

        // Read the last lines and print them
        int count = 0;
        while (true) {
            try {
                Line line = readLine();
                if (line == null) {
                    break;
                }
                if (count == 0) {
                    count++;
                    continue;
                }
                // TODO - spurious stars 03/19/20 mats 9-11
                if ((count <= 20) || (count >= 72550)) {
                    System.out.println("count=" + count + ",date=" + line.date +
                            ",mat=" + line.mat + ",first=" + line.first +
                            ",last=" + line.last + ",type=" + line.type +
                            ",comments=" + line.comments);
                }
                count++;
            } catch(IOException e){
                System.out.println("Read error: " + e.getMessage());
                System.exit(6);
            }
        }

    }

        // Support Methods -------------------------------------------------------

        /**
         * <p>Create and return a date formatter that knows how to parse dates like "01/05/20"
         * or "1/5/2020".</p>
         */
        public static DateTimeFormatter createDateTimeFormatter() {
            // Accept two-digit or four-digit years, two digits means "20yy"
            return DateTimeFormatter.ofPattern("M/d/[uuuu][uu]");
        }

        /**
         * <p>Create and return a scanner appropriate for reading CSV files.</p>
         *
         * @param pathname Pathname to the file to be read
         *
         * @return A configured {@link Scanner} instance
         *
         * @throws FileNotFoundException if the specified pathname cannot be resolved
         */
        public static Scanner createScanner (String pathname) throws FileNotFoundException {
            Scanner scanner = new Scanner(new File(pathname));
            scanner.useDelimiter(",");
            return scanner;
        }

        /**
         * <p>Parse a date string using either YYYY-MM-DD or MM/DD/YY (or MM/DD/YYYY) formats.</p>
         *
         * @param input Input string to be parsed
         *
         * @return LocalDate for the parsed value
         */
        public static LocalDate parseDate (String input) throws DateTimeParseException {
            try {
                return LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException e) {
                return LocalDate.parse(input, alternateDateTimeFormatter);
            }

        }

        public static Line readLine() throws IOException {
            String text = reader.readLine();
            if (text == null) {
                return null;
            }
            String[] fields = text.split(",");
            Line line = new Line();
            line.date = fields[0];
            line.mat = fields[1];
            line.first = fields[2];
            line.last = fields[3];
            line.type = fields[4];
            line.comments = fields[6];
            return line;
        }

        static class Line {
            public String date;
            public String mat;
            public String first;
            public String last;
            public String type;
            public String comments;
        }

    }
