# dir-stats-exporter

OTLP Exporter that reports file count and last modified information.

## Exported Metrics

| Name                   | Attributes          | Description                                   |
|------------------------|---------------------|-----------------------------------------------|
| dir_stats_count        | path, hidden, empty | Number of files in a directory                |
| dir_stats_bytes        | path, hidden, empty | Sum of all file sizes in a directory in bytes |
| dir_stats_oldest_ts    | path, hidden, empty | Timestamp of oldest file in seconds           |
| dir_stats_oldest_age   | path, hidden, empty | Age of oldest file in seconds                 |
| dir_stats_oldest_bytes | path, hidden, empty | Size of oldest file in bytes                  |
| dir_stats_newest_ts    | path, hidden, empty | Timestamp of newest file in seconds           |
| dir_stats_newest_age   | path, hidden, empty | Age of newest file in seconds                 |
| dir_stats_newest_bytes | path, hidden, empty | Size of newest file in bytes                  |

## Example Configuration

```json
{
  "endpoint": "http://otel-exporter",
  "defaultInterval": "1 minute",
  "directories": [
    {
      "path": "/test",
      "interval": "10 minutes"
    }
  ]
}
```
