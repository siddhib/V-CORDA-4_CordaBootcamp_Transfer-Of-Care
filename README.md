# Transfer of Care CorDapp
Implement a CorDapp to digitize transfer of care for a person between medical and social organizations.
A medical record is maintained at the Municipal council for all citizens. A hospital will gain access to this record while a patient is under its care and can add additional info to it.
The Municipal council always has the latest copy of all the records.

Once a hospital is done treating the patient, they can discharge her.
In case the patient needs further care, the hospital raises a ‘Transfer of care’ request with the Municipal Council which can review and approve\reject it.
In case of acceptance the status on the EHR should change accordingly to reflect the transfer of care and hospital should not be able to see or modify the EHR.


## Set up

1. `git clone https://github.com/siddhib/V-CORDA-4_CordaBootcamp_Transfer-Of-Care`
2. cd V-CORDA-4_CordaBootcamp_Transfer-Of-Care
3. ./gradlew deployNodes - building may take upto a minute (it's much quicker if you already have the Corda binaries)./r
4. cd build/nodes
5. ./runnodes

At this point you will have a notary node running as well as three other nodes and their corresponding webservers. There should be 7 console windows in total. One for the notary and two for each of the three nodes. The nodes take about 20-30 seconds to finish booting up.

# Testing this solution using Rest apis

1. Hospital A: http://localhost:10010
2. Hospital B: http://localhost:10011
3. Municipal Council: http://localhost:10012

**Step 1: Admission flow on Hospital A node**
```
PUT request
http://localhost:10010/api/hospital/admit?ehrID=abc&partyName=Municipal Council
```

**Step 2: Add medical event on Hospital A node**
```
PUT request
http://localhost:10010/api/hospital/addEvent?ehrID=abc&partyName=Municipal Council&medicalEvent=Alloted Bed
```

**Step 3: Initiate TOC request on Hospital A node**
```
PUT Request
http://localhost:10010/api/hospital/initiateTOC?partyName=Municipal Council&toHospital=Hospital B&ehrID=abc
```

**Step 4: Approve TOC on Municipal Council node**
```
PUT Request
http://localhost:10012/api/muncipal/reviewTOC?ehrID=abc&status=Approve
```

OR

**Step 4: Reject TOC on Municipal Council node**
```
PUT Request
http://localhost:10012/api/muncipal/reviewTOC?ehrID=abc&status=Reject
```
In this case, Repeat step 3

**Step 5: Check if hospital B has access to patient details and Hospital A should not have access**
```
GET Request
http://localhost:10010/api/hospital/states
```

**Step 6: Admit patient to hospital B**
```
PUT Request
http://localhost:10011/api/hospital/admit?ehrID=abc&partyName=Municipal Council
```

**Step 7:Discharge from hospital B**
```
PUT Request
http://localhost:10011/api/hospital/discharge?dischargeDocument=C:\discharge2.zip&ehrID=abc&partyName=Municipal Council
```

