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

import org.cityteam.guests.action.Import;
import org.cityteam.guests.client.FacilityClient;
import org.cityteam.guests.model.Facility;
import org.cityteam.guests.model.Registration;
import org.cityteam.guests.model.types.FeatureType;
import org.cityteam.guests.model.types.PaymentType;
import org.craigmcc.library.shared.exception.BadRequest;
import org.craigmcc.library.shared.exception.InternalServerError;
import org.craigmcc.library.shared.exception.NotFound;
import org.craigmcc.library.shared.exception.NotUnique;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static org.cityteam.guests.client.AbstractClient.PROPERTY_BASE_URI;
import static org.cityteam.guests.model.types.FeatureType.H;
import static org.cityteam.guests.model.types.FeatureType.S;
import static org.cityteam.guests.model.types.PaymentType.$$;
import static org.cityteam.guests.model.types.PaymentType.FM;

public class Main {

    // Accept mm/dd/yy or mm/dd/yyyy
    public static DateTimeFormatter alternateDateTimeFormatter = createDateTimeFormatter();
    public static LocalDate dummyDate = LocalDate.parse("2999-12-31");
    public static List<Import> imports = new ArrayList<>();
    public static Facility facility;
    public static final FacilityClient facilityClient = new FacilityClient();
    public static LocalDate fromDate;
    public static String pathname;
    public static BufferedReader reader;
    public static LocalDate registrationDate = null;
    public static List<Registration> registrations = new ArrayList<>();
    public static boolean skipRest = false;
    public static LocalDate toDate;

    // Main Program ----------------------------------------------------------

    public static void main(String[] args) {

        processArguments(args);
        setUpReader();
        acquireFacility();

        // Skip header line
        try {
            readLine();
        } catch (IOException e) {
            System.out.println("Cannot skip header line: " + e.getMessage());
            System.exit(21);
        }

        // Process all dates in the requested range
        registrationDate = null;
        while (true) {
            try {
                Line line = readLine();
                if (line == null) {
                    break;
                }
                if (line.date.compareTo(fromDate) < 0) {
                    continue;
                }
                if (line.date.compareTo(toDate) > 0) {
                    break;
                }
                if (registrationDate == null) {
                    beginDate(line.date);
                } else if (!registrationDate.equals(line.date)) {
                    endDate(registrationDate);
                    beginDate(line.date);
                }
                if (skipRest) {
                    continue;
                }
                processLine(line);
            } catch (IOException e) {
                System.out.println("Read error: " + e.getMessage());
                System.exit(22);
            }
        }
        if (registrationDate != null) {
            try {
                endDate(registrationDate);
            } catch (Exception e) {
                System.out.println("Send exception: " + e.getMessage());
            }
        }

        /*
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
*/

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
     * <p>Begin processing rows for the specified date.</p>
     */
    public static void beginDate(LocalDate localDate) {
        registrationDate = localDate;
        imports.clear();
        registrations.clear();
        skipRest = false;
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
     * <p>End processing rows for the specified date.</p>
     */
    public static void endDate(LocalDate localDate) {
        skipRest = false;
        if (imports.size() == 0) {
            return;
        }
        System.out.println("Send:  " + localDate.toString() +
                " rows: " + imports.size() +
                (!isNormalSize(imports.size()) ? " ***" : ""));
        try {
            registrations = facilityClient.importRegistrationsByFacilityAndDate(
                    facility.getId(),
                    localDate,
                    imports
            );
        } catch (BadRequest badRequest) {
            badRequest.printStackTrace();
        } catch (InternalServerError e) {
            System.out.println("SEND ISE: " + e.getMessage());
        } catch (NotFound e) {
            System.out.println("SEND NF:  " + e.getMessage());
        } catch (NotUnique e) {
            System.out.println("SEND NU:  " + e.getMessage());
        }
        registrationDate = null;
    }

    public static boolean isNormalSize(int count) {
        if ((count == 24) | (count == 42) || (count == 58)) {
            return true;
        } else {
            return false;
        }
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
            try {
                return LocalDate.parse(input, alternateDateTimeFormatter);
            } catch (Exception exception) {
                System.out.println("CANNOT PARSE DATE: " + input);
                return dummyDate;
            }
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
     * <p>Format an {@link Import} for the specified line, and add it to
     * the list we will be sending.</p>
     */
    public static void processLine(Line line) {
        if ((line.first != null) && (line.first.startsWith("***"))) {
            skipRest = true;
            return;
        }
        Import newImport = new Import(
                line.comments,
                line.features,
                line.first,
                line.last,
                line.matNumber,
                line.amount,
                line.type,
                null,
                null
        );
        imports.add(newImport);
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
        if ("".equals(fields[0])) {
            return null;
        }
        Line line = new Line();
        if ("".equals(fields[0]) || "Date".equals(fields[0])) {
            return line;
        }
        line.date = parseDate(fields[0]);
        String mat = fields[1];
        if (mat.endsWith("HS") || mat.endsWith("SH")) {
            mat = mat.substring(0, mat.length() - 2);
            line.features = List.of(H, S);
        } else if (mat.endsWith("H")) {
            mat = mat.substring(0, mat.length() - 1);
            line.features = List.of(H);
        } else if (mat.endsWith("S")) {
            mat = mat.substring(0, mat.length() - 1);
            line.features = List.of(S);
        }
        try {
            line.matNumber = Integer.parseInt(mat);
        } catch (NumberFormatException e) {
            System.out.println("Cannot parse matNumber '" + mat +
                    "' from '" + fields[1] + "'");
            line.matNumber = 99;
        }
        line.first = "".equals(fields[2]) ? null : fields[2];
        line.last = "".equals(fields[3]) ? null : fields[3];
        if ((line.first != null) && (line.last == null)) {
            line.last = "?????";
        } else if ((line.first == null) && (line.last != null)) {
            line.first = "?????";
        }
        if ("".equals(fields[4])) {
            line.type = null;
        } else {
            try {
                line.type = PaymentType.valueOf(fields[4].toUpperCase());
            } catch (IllegalArgumentException e) {
                System.out.println("Cannot parse paymentType '" +
                        fields[4] + "'");
                line.type = FM;
            }
        }
        if ($$.equals(line.type)) {
            line.amount = BigDecimal.valueOf(5.00);
        } else {
            line.amount = null;
        }
        line.comments = "".equals(fields[6]) ? null : fields[6];
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
        public LocalDate date;
        public List<FeatureType> features;
        public Integer matNumber;
        public String first;
        public String last;
        public BigDecimal amount;
        public PaymentType type;
        public String comments;
    }

}
