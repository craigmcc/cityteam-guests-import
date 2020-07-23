# CityTeam Guests Importer

Import program that can process a CSV file from the spreadsheet currently
used by CityTeam Portland for registering overnight guests.
 
#### Command Line Execution

Square brackets indicate optional parameters, and should not be included
literally.

```.shell script
java -jar cityteam-guests-importer.jar \
  {fromDate} {toDate} {serverUrl} {pathToCSVFile}
```

#### Input CSV File Format

The very first line is assumed to be column headers and is ignored.

The following comma-separated columns are expected in each line.

| Field Name | Contents |
| :---: | --- |
| Date | Registration date for this entry, in MM/DD/YY format.  20 is assumed for the century |
| Mat# | Mat number, optionally suffixed with feature identifier(s) - See below. |
| Column1 | First name of the overnight guest (field name is a misnomer). 
| Last Name | Last name of the overnight guest. |
| PAY | Payment type code -- See below. |
| BAC | Unknown, ignored. |
| Comments | Any comments noted about this guest for this night. |
| Exclude? | Unknown, ignored. |
| #FM 30 Days | Unknown, ignored. |

Valid feature identifiers are as follows:

| Code | Description |
| :---: | --- |
| H | Handicap mat |
| S | Socket (electricity) nearby |

Valid payment type codes are as follows (no cash if not $$):

| Code | Description |
| :---: | --- |
| $$ | Paid Cash (assumed to be $5.00) |
| AG | Agency Voucher |
| CT | CityTeam Decision |
| FM | Unknown |
| MM | Medical Mat |
| SW | Severe Weather |

