# dir-stats-exporter

OTLP Exporter that reports file count and last modified information

## Example Configuration

```json
{
  "endpoint": "http://otel-exporter",
  "defaultInterval": "1 minute",
  "directories": [
    {
      "path": "/test",
      "includeHidden": false,
      "interval": "10 minutes"
    }
  ]
}
```
