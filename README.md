# AgroecoPlan : Agroecological crop allocation solver for small farmers

**Note: AgroecoPlan is in its early stages of development, and can only be considered as a prototype / proof of concept
at the moment**

AgroecoPlan is a open and flexible decision support tool based in
[Constraint Programming](https://en.wikipedia.org/wiki/Constraint_programming) that supports small farmers in the 
allocation of their annual vegetable crops by taking into account constraints that are typical of small farms.
These constraint can be operational, agroecological, pedo-climatic, etc.
Because each farm and each context is unique, flexibility is one of the key principles that guided the design of the
tool. AgroecoPlan relies on [Choco-solver](https://choco-solver.org/).

AgroecoPlan tackles a complex combinatorial problem in which the input data is the following:

- A csv file describing the cropping calendar of a small farm. The cultivation period of each crop is already defined,
according to production objectives. This file can also contain information about crops requirements
(e.g. vegetables beds that are forbidden because they do not respect the species light or soil requirements).
- A csv file describing the farm: vegetable beds, and their adjacency relationship.
- A csv file describing the interactions between cultivated species: negative (-1), neutral (0), or beneficial (1).
- A csv file describing the interactions between preceding crops (according to their species).
- A csv file describing the necessary return delay between crops (according to their species).

According to a set of constraints and, eventually, an optimization objective (both defined by the user), AgroecoPlan
identifies and output crop allocation plans that satisfies the constraints and optimize the objective (if defined).
Note that if the constraints cannot be satisfied, because they are too restrictive given the configuration of the farm
and the crop calendar, AgroecoPlan is able to detect and guarantee the absence of solutions.

Currently, the following constraints are available within AgroecoPlan:

- C1: Ensuring a return delay between two crops from the same botanical family on the same vegetable beds.
- C2: Forbid the adjacency of crops that have negative interactions.
- C3: Dilute crops from the same species, i.e. forbid their adjacency.
- C4: Forbid a set of vegetable bed to certain crops, because they do not satisfy cultivation or operational
requirements.
- C5: Group identical crops, i.e. force crops that are from the same species and cultivated during the same period to be
allocated on a connected set of vegetable beds.
- C6: Forbid negative precedences.

The following optimization objective are currently available within AgroecoPlan:

- SAT: No optimization, only satisfy constraints.
- O1: Maximize the number of adjacencies between crops that have beneficial interactions.
- O2: Maximize the number of positive precedences.

## Usage

AgroecoPlan is currently distributed as a command-line prototype, which can be downloaded here (put download link).

To use it, type the following command in a terminal: `java -jar agroecoplan-0.1.jar`

```shell
Usage: <main class> [-psv] [-c=<nbCores>] [-cst=<constraints>]
                    [-opt=<optimizationObjective>] [-t=<timeout>] <needsFile>
                    <bedsFile> <interactionsFile> <precedenceFile> <delaysFile>
                    <output>
Agroecological crop allocation problem solver
      <needsFile>           Path of the CSV file describing the crop calendar
      <bedsFile>            Path of the CSV file describing the farm (vegetable
                              beds and their adjacency relation)
      <interactionsFile>    Path of the CSV file describing interactions
                              between species
      <precedenceFile>      Path of the CSV file describing precedences
                              interactions between species
      <delaysFile>          Path of the CSV file describing delay interactions
                              between species
      <output>              Output file path
  -c, --cores=<nbCores>     If parallel search is set, define the number of
                              cores to use
      -cst, --constraints=<constraints>
                            Comma-separated list of the constraints to enforce
                              (e.g. -cst C1,C2).Currently available constraints
                              are:
                            -C1: Enforce return delays
                            -C2: Forbid negative interactions between adjacent
                              crops
                            -C3: Dilute identical crop, i.e. forbid adjacency
                              between crops from the same species
                            -C4: Forbid some beds (e.g. due to light
                              requirements). The information of forbidden beds
                              is in the crop calendar file
                            -C5: group identical crops, i.e. force crops from
                              the same species and same cultivation period to
                              be allocated to a connected set of vegetable beds
                            -C6: forbid negative precedences
      -opt, --optimization-objective=<optimizationObjective>
                            Optimization objective to use. Currently available
                              objectives are:
                            -SAT: Constraint satisfaction only, no optimization
                              objective
                            -O1: Maximize the number of positive interactions
                            -O2: Maximize the number of positive precedences
                            Default is SAT
  -p, --parallel            If used, parallelize the search using a parallel
                              portfolio
  -s, --show                If true, display the solution
  -t, --timeout=<timeout>   Time limit of the search, use -1 for no time limit
  -v, --verbose             If true, display information useful for debug
```
