# dir-stats-exporter

OTLP Exporter that reports file count and last modified information.

## Exported Metrics

| Name                               | Attributes                | Description                                          |
|------------------------------------|---------------------------|------------------------------------------------------|
| dir_stats_duration_seconds         | path                      | Time it took to list the directory in seconds        |
| dir_stats_timestamp_seconds        | path                      | Modification timestamp of the directory in seconds   |
| dir_stats_age_seconds              | path                      | Modification age of the directory in seconds         |
| dir_stats_count                    | path, hidden, empty       | Number of files in a directory                       |
| dir_stats_bytes                    | path, hidden, empty       | Sum of all file sizes in a directory in bytes        |
| dir_stats_oldest_timestamp_seconds | path, hidden, empty, name | Modification timestamp of the oldest file in seconds |
| dir_stats_oldest_age_seconds       | path, hidden, empty, name | Modification age of the oldest file in seconds       |
| dir_stats_oldest_bytes             | path, hidden, empty, name | Size of the oldest file in bytes                     |
| dir_stats_newest_timestamp_seconds | path, hidden, empty, name | Modification timestamp of the newest file in seconds |
| dir_stats_newest_age_seconds       | path, hidden, empty, name | Modification age of the newest file in seconds       |
| dir_stats_newest_bytes             | path, hidden, empty, name | Size of the newest file in bytes                     |

## Example Configuration

```json
{
  "collectorEndpoint": "http://otel-collector",
  "interval": "1 minute",
  "directories": [
    {
      "path": "/test",
      "interval": "10 minutes",
      "recursive": false,
      "include": [
        ".*"
      ],
      "exclude": [
        "test"
      ]
    }
  ]
}
```

- The `include` and `exclude` patterns are anchored.
