# dir-stats-exporter

OTLP Exporter that reports file count and last modified information.

## Exported Metrics

| Name                   | Attributes | Description                                   |
|------------------------|------------|-----------------------------------------------|
| dir_stats_count        | path       | Number of files in a directory                |
| dir_stats_count_empty  | path       | Number of empty files in a directory          |
| dir_stats_bytes        | path       | Sum of all file sizes in a directory in bytes |
| dir_stats_oldest_ts    | path       | Timestamp of oldest file in seconds           |
| dir_stats_oldest_age   | path       | Age of oldest file in seconds                 |
| dir_stats_oldest_bytes | path       | Size of oldest file in bytes                  |
| dir_stats_newest_ts    | path       | Timestamp of newest file in seconds           |
| dir_stats_newest_age   | path       | Age of newest file in seconds                 |
| dir_stats_newest_bytes | path       | Size of newest file in bytes                  |

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
