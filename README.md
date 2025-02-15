# Illumio Tech Assessment

## Project Structure

```
/
├── asset/          # External resources
│   └── protocol-numbers.csv   # IANA protocol number records
├── result/         # Output files from analysis
│   └── *.log      # Generated sample analysis reports
├── src/           # Source code
└── test/          # Test files and test cases
```

### Directory Details

* **/asset**: Contains reference data including the IANA protocol numbers from [IANA Protocol Numbers Registry](https://www.iana.org/assignments/protocol-numbers/protocol-numbers.xhtml#protocol-numbers-1)
  * The file is adapted and interpreted in the following :
    * removed deprecated flags
    * ignored protocols with an empty keyword (e.g. 61, 63, 68, 99, 114)
* **/result**: Stores analysis output files by default
* **/test**: Contains test files and generated test cases by default

## Prerequisites

* JDK 18 or higher
* IntelliJ IDEA (recommended)

## Compile and Run
Set the working directory to the **project root (/)** and compile the source file

### Running the Flow Log Parser

```bash
java FlowLogParser <path-to-lookup-table> <path-to-flow-log> [<path-to-output-file>]
```

Parameters:
* `path-to-lookup-table`: Path to the CSV file containing port/protocol to tag mappings
* `path-to-flow-log`: Path to the flow log file to analyze
* `path-to-output-file`: (Optional) Custom output path. Defaults to `result/${timestamp}.log`

### Running the Test Case Generator

```bash
java TestCaseGenerator [<numMappings>] [<numLogs>]
```

Parameters:
* `numMappings`: (Optional) Number of lookup table entries to generate (default: 10000)
* `numLogs`: (Optional) Number of flow log entries to generate (default: 100000)

## Assumptions
* The input file follows the format in sample test, in plain text
* This program only supports default log format, version 2

## Tests

### Test Case Generator

(This part is generated by Claude 3.5 Sonnet)

The test case generator creates:
1. Lookup table entries with:
    * Realistic port numbers
    * Protocol names from IANA registry
    * Randomly generated service tags
2. Flow log entries with:
    * Valid IP addresses (public and private ranges)
    * Common and random ports
    * Timestamps within a reasonable range
    * Realistic packet and byte counts

### Test Cases and Performance

#### 1. Sample Test
* Input: simple testcase in the email
* Purpose:
    * Verify basic functionality, the case of multiple mapping of tag
    * Easy to validate results manually

#### 2. Large File Test
* Input:
    * Large lookup table (10,000 entries)
    * Large flow log (100000 entries, approx.10M size. 11.6M sample provided)
* Performance: < 1s average

## Design Choices and Future Improvement
### Custom objects (record) / Composite String as combination identifier
* To uniquely identify port-protocol combinations for lookup and counting purposes, a Java record is used as the composite key. 
Since Java 14 (formally in Java 16), records are specially optimized for this exact use case - representing immutable data groups. 

* While a simpler string concatenation approach (like "port|protocol") could have worked, the record offers better type safety 
and more explicit domain modeling without significant memory overhead.


### Flyweight Pattern and String deduplication
* Based on the design choice with a record as key, a new one needs to be created when processing each log
entry. Noticing the unnecessary memory usage, **flyweight pattern** was applied to allow object (record) sharing.
A factory is created to manage the pool of keys while ensuring thread safety by convention.

* Since the number of possible protocols is limited, String deduplication using **intern()** was considered but
not implemented. By theory, intern() can avoid duplicated String object creation by internally managing a
pool of String objects and thus save memory. But in the specific case, overhead obviously exceeds benefit.

### Multi-threading: 
The program is by default single-threaded. Although multi-threading may be able to boost performance, 
the gain exceeds the overhead only when the files to process are very large. In the given workload up 
to 10M/10000 or even smaller, multi-threading doesn't bring a non-negligible performance boost.

### File I/O:
To meet the requirement of not using external packages, file I/O is implemented with standard library operations
accompanied by stream pipelines. Third-party libraries like FasterXML Jackson may be able to speed up the 
I/O processes.

