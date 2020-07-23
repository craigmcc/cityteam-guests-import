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

import org.cityteam.guests.client.FacilityClient;
import org.cityteam.guests.model.Facility;
import org.craigmcc.library.shared.exception.BadRequest;
import org.craigmcc.library.shared.exception.InternalServerError;
import org.craigmcc.library.shared.exception.NotFound;
import org.craigmcc.library.shared.exception.NotUnique;

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
    public static Facility facility;
    public static final FacilityClient facilityClient = new FacilityClient();
    public static LocalDate fromDate;
    public static String pathname;
    public static BufferedReader reader;
    public static LocalDate toDate;

    // Main Program ----------------------------------------------------------

    public static void main(String[] args) {

        processArguments(args);
        setUpReader();
        acquireFacility();

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
     * <p>Acquire the {@link Facility} for Portland, creating one if necessary.</p>
     */
    public static void acquireFacility() {
        try {
            facility = facilityClient.findByNameExact("Portland");
        } catch (InternalServerError e) {
            System.out.println("Fatal error retrieving facility: " + e.getMessage());
            System.exit(11);
        } catch (NotFound e) {
            try {
                facility = facilityClient.insert(new Facility(
                        "526 SE Grand Ave.",
                        null,
                        "Portland",
                        "portland@cityteam.org",
                        "Portland",
                        "503-231-9334",
                        "OR",
                        "97214"
                ));
            } catch (BadRequest f) {
                System.out.println("Data error creating facility: " + f.getMessage());
                System.exit(12);
            } catch (InternalServerError f) {
                System.out.println("Fatal error creating facility: " + f.getMessage());
                System.exit(13);
            } catch (NotUnique f) {
                System.out.println("Uniqueness error creating facility: " + f.getMessage());
                System.exit(14);
            }
        }
        System.out.println("facility= " + facility);
    }

    /**
     * <p>Create and return a date formatter that knows how to parse dates like "01/05/20"
     * or "1/5/2020".</p>
     */
    public static DateTimeFormatter createDateTimeFormatter() {
        // Accept two-digit or four-digit years, two digits means "20yy"
        return DateTimeFormatter.ofPattern("M/d/[uuuu][uu]");
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

    /**
     * <p>Process command line arguments and set the corresponding variables.</p>
     */
    public static void processArguments(String[] args) {

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
        pathname = args[2];
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

    }

    /**
     * <p>Read a single line from the input reader, and parse out the fields we need
     * as strings.</p>
     *
     * @return {@link Line} containing parsed fields, or <code>null</code> for EOF
     *
     * @throws IOException If an I/O exception occurs
     */
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

    /**
     * <p>Set up a reader to process the incoming lines.</p>
     */
    public static void setUpReader() {
        try {
            reader = new BufferedReader(new FileReader(pathname));
        } catch (FileNotFoundException e) {
            System.out.println("Pathname '" + pathname + "' cannot be found");
            System.exit(5);
        }
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
