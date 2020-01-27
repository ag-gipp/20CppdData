# CPPD DATA
This repository contains the data and code to reproduce the experiments in our papers on Content Protecting Plagiarism Detection. 

## Cases
[/cases/confirmed_plag](/cases/confirmed_plag)  
contains the confirmed cases of academic plagiarism in STEM research publications we used as a test collection.

We provide the original PDF versions of the cases and the cases' representation in the TEI-based unified document format, as preprocessed by our prototype.

## Document Collection
We embedded our test cases in the collection of the NTCIR-12 MathIR Task. For research purposes, the dataset is available free of charge [here](http://research.nii.ac.jp/ntcir/permission/ntcir-12/perm-en-MathIR.html).


**1. [hyplag-backend](https://github.com/ag-gipp/hyplag-backend)** our provided code complements the hyplag-backend by providing a CLI for the hash-generation, data-base imports, and detection using hashed sets.



