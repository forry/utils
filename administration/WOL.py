#!/usr/bin/env python
# wol.py

import socket
import struct
import sys

cadwork2='f46d04617ba0'
pcmilet='00270e1255cb'

def wake_on_lan(macaddress):
    """ Switches on remote computers using WOL. """

    # Check macaddress format and try to compensate.
    if len(macaddress) == 12:
        pass
    elif len(macaddress) == 12 + 5:
        sep = macaddress[2]
        macaddress = macaddress.replace(sep, '')
    else:
        raise ValueError('Incorrect MAC address format')
 
    # Pad the synchronization stream.
    data = ''.join(['FFFFFFFFFFFF', macaddress * 20])
    send_data = '' 

    # Split up the hex values and pack.
    for i in range(0, len(data), 2):
        send_data = ''.join([send_data,
                             struct.pack('B', int(data[i: i + 2], 16))])

    # Broadcast it to the LAN.
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.sendto(send_data, ('<broadcast>', 7))
    

if __name__ == '__main__':
    if len(sys.argv) < 2:
      print 'usage WOL.py <macaddress>\n'
    else:
      if sys.argv[1] == 'pcmilet':
         wake_on_lan(pcmilet)
      elif sys.argv[1] == 'cadwork2':
         wake_on_lan(cadwork2)
      else:
         wake_on_lan(sys.argv[1])
