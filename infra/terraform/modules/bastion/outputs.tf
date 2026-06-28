output "instance_id" {
  description = "Bastion EC2 instance ID."
  value       = aws_instance.main.id
}

output "public_ip" {
  description = "Bastion public IP address."
  value       = aws_instance.main.public_ip
}

output "public_dns" {
  description = "Bastion public DNS name."
  value       = aws_instance.main.public_dns
}
