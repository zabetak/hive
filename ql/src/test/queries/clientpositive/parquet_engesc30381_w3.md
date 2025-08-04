# Use custom tzdata rules in JDK

This document outlines the steps to modify the tzdata rules and update the JDK installation to use them.

1. Create a temporary directory to isolate JDK changes
```bash
mkdir -p /tmp/ENGESC-30381
cd /tmp/ENGESC-30381
```
2. Download [Java SE Timezone Updater 2.3.3](https://www.oracle.com/java/technologies/javase-tzupdater-downloads.html)
3. Extract the contents of the archive
```bash
unzip tzupdater-2.3.3.zip
```
4. Check where the JDK is installed and copy the entire directory since it is going to be modified
```bash
cp -r $JAVA_HOME jdk_copy
```
5. Check the current version of the tzdata package that is used using the tzupdater tool
```bash
jdk_copy/bin/java -jar tzupdater.jar -V
tzupdater version 2.3.3-b02
JRE tzdata version: tzdata2025a
tzupdater tool would update with tzdata version: tzdata2025b
```
6. Download the same tzdata version package from https://data.iana.org/time-zones/releases/
```bash
wget https://data.iana.org/time-zones/releases/tzdata2025a.tar.gz
```
You can opt to donwload a different version if needed, but picking the same would minimize the risk of introducing other breaking changes.
7. Create a directory to host the modifications to tzdata package
```bash
mkdir tzdata2025custom
```
8. Extract the contents of the original tzdata archive into the newly created directory
```bash
tar -xvf tzdata2025a.tar.gz -C tzdata2025custom/
```
9. Open the file that holds the rules for the desired timezone (America/Phoeniex)
```bash
gedit tzdata2025custom/northamerica
```
10. Look for "Zone America/Phoenix" inside the file to identify the rules that need to be modified
11. Remove the entry that defines a special offset for dates before 1883 (i.e., "-7:28:18 -	LMT	1883 Nov 18 19:00u").
The modified entry should look like this:
```text
# Zone	NAME		STDOFF	RULES	FORMAT	[UNTIL]
Zone America/Phoenix	-7:00	US	M%sT	1944 Jan  1  0:01
			-7:00	-	MST	1944 Apr  1  0:01
			-7:00	US	M%sT	1944 Oct  1  0:01
			-7:00	-	MST	1967
			-7:00	US	M%sT	1968 Mar 21
			-7:00	-	MST
```
13. For traceability purposes open and update the version file
```bash
gedit tzdata2025custom/version
```
14. Change the identifier to indicate that this is custom build (e.g., replace 2025a with 2025a.MyCompany.Aug.4)
15. Repackage the modified tzdata rules into an archive
```bash
cd tzdata2025custom/ && tar -czvf ../tzdata2025custom.tar.gz * && cd ..
```
16. Use the tzupdater tool to update the rules inside the JDK installation
```bash
jdk_copy/bin/java -jar tzupdater.jar -l file:///tmp/ENGESC-30381/tzdata2025custom.tar.gz
```
17. Change JAVA_HOME and any other application specific properties to point to the modified JDK installation
```bash
export JAVA_HOME=/tmp/ENGESC-30381/jdk_copy
```