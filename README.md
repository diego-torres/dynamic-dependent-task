# dynamic-dependent-task
Dynamic Dependent Task

## Requirements
- Add a dynamic task between two existing tasks.
- The task can be a "Service Task", a "Human Task" or a "Sub-process" (Callable activity).

## Known solutions

Using Case Management dynamic tasks and adhoc tasks can be added in runtime and the data generated from those tasks can drive the execution of milestones and stages.
The requirement is to add a task to an existing BPMN process, the default flow of the process is defined, but during runtime it is decided that between two nodes another node will be required for execution.

## Known problem

The insertion of dynamic tasks in the BPMN flow will invalidate tests and expected behavior, this proof of concept is not a recommended practice, it is presented as the solution of an specific requirement.