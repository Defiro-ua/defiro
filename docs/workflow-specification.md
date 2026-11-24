# The workflow file

A workflow travels as one JSON file: its **specification**, the format the Workflow Designer publishes and the Workflow Engine keeps as an immutable version (`packages/spec`, `WorkflowSpecification`).

- **Download.** In «Workflows», «Descarregar» gives the published version of a workflow, named after it and its version: `ajudas-de-custo-v1.json`. It is exactly what the Engine runs.
- **Load.** In «Workflows», «Carregar ficheiro» turns a file into a **new draft**. The designer opens it on «Rever e criar». Nothing is published until the Workflow Manager chooses «Criar workflow»; then the Designer checks it against every rule, as it checks any draft, and deploys it to the Engine as version 1 of a new workflow.

API, for Workflow Managers only:

| | |
|---|---|
| `GET /api/designer/drafts/{id}/specification` | the last published version, as a file (404 if never published) |
| `POST /api/designer/drafts/import` | the file as the body; answers 201 with the new draft, or 400 with why the file was refused |

## Fields

| Field | What it is |
|---|---|
| `schemaVersion` | The version of this format: `1`. A defiro refuses a file of a version it does not know. |
| `name` | The workflow's name, as people see it in «Novo pedido». |
| `steps` | The path, in order. The first step is the request. |
| `steps[].id` | The step's id, unique in the file. Transitions name steps by it. |
| `steps[].plugin` | The step type, a plugin of the Registry's catalogue: `{ "name": "review", "version": "0.1.0" }`. The built-in ones are `submission` (the request), `review`, `final-validation` and `document-generation`. |
| `steps[].name` | The step's name in the path, for example «Validação da chefia». |
| `steps[].handledBy` | The group or unit that handles the step; for the request, who can make it. |
| `steps[].deadline` | The legal deadline in working days, from 1 to 90; `null` for the request. |
| `steps[].returnReasons` | Why the step may return the request to the requester, to be corrected. |
| `steps[].rejectReasons` | Why the step may reject the request for good. |
| `transitions` | Where a request goes when a step ends with an outcome: `{ "from", "outcome", "to" }`, `to` being `null` when the procedure ends. |

The transitions follow the path: «approved» goes to the next step, «returned» back to the request, «rejected» to the end. A loaded file's transitions are not read: the Designer makes them again from the order of the steps when publishing, so a file may leave them out.

## Example

```json
{
  "schemaVersion": 1,
  "name": "Ajudas de custo",
  "steps": [
    {
      "id": "pedido",
      "plugin": { "name": "submission", "version": "0.1.0" },
      "name": "Pedido",
      "handledBy": "Todos os trabalhadores",
      "deadline": null,
      "returnReasons": [],
      "rejectReasons": []
    },
    {
      "id": "chefia",
      "plugin": { "name": "review", "version": "0.1.0" },
      "name": "Validação da chefia",
      "handledBy": "Chefias da unidade do requerente",
      "deadline": 5,
      "returnReasons": ["Preenchido incorretamente"],
      "rejectReasons": ["Pedido não autorizado"]
    },
    {
      "id": "final",
      "plugin": { "name": "final-validation", "version": "0.1.0" },
      "name": "Validação final",
      "handledBy": "Divisão Financeira",
      "deadline": 10,
      "returnReasons": ["Preenchido incorretamente"],
      "rejectReasons": ["Pedido não autorizado"]
    }
  ],
  "transitions": [
    { "from": "pedido", "outcome": "submitted", "to": "chefia" },
    { "from": "chefia", "outcome": "approved", "to": "final" },
    { "from": "chefia", "outcome": "returned", "to": "pedido" },
    { "from": "chefia", "outcome": "rejected", "to": null },
    { "from": "final", "outcome": "approved", "to": null },
    { "from": "final", "outcome": "returned", "to": "pedido" },
    { "from": "final", "outcome": "rejected", "to": null }
  ]
}
```

## Changing the format

A change that a defiro of the previous version could not read takes the next `schemaVersion`, and the Designer goes on reading the versions before it.
