"""Optional desktop QR invitation window; not injected into the game's title-screen renderer.

Dependencies: pip install "qrcode[pil]" ; Tkinter is included in standard Windows Python.
"""
import io
import tkinter as tk
from tkinter import ttk


def show_window(state, advertise_host, port=8765):
    try:
        import qrcode
        from PIL import Image, ImageTk
    except ImportError as exc:
        raise RuntimeError('Install QR dependencies: python -m pip install "qrcode[pil]"') from exc

    root = tk.Tk()
    root.title('Pip-Boy Remote — Scan to Pair')
    root.configure(bg='#081208')
    root.attributes('-topmost', True)
    root.resizable(False, False)
    root.geometry('380x500+90+90')

    tk.Label(root, text='PIP-BOY 3000 REMOTE', fg='#A1FF93', bg='#081208',
             font=('Consolas', 17, 'bold')).pack(pady=(22, 8))
    tk.Label(root, text='SCAN ON YOUR PHONE', fg='#A1FF93', bg='#081208',
             font=('Consolas', 12)).pack()
    qr_label = tk.Label(root, bg='white', padx=10, pady=10)
    qr_label.pack(pady=18)
    status = tk.StringVar(value='Preparing one-use invite…')
    tk.Label(root, textvariable=status, fg='#A1FF93', bg='#081208',
             wraplength=345, font=('Consolas', 10)).pack(pady=8)
    tk.Label(root, text='Trusted private Wi-Fi only • HTTP is not encrypted',
             fg='#F3CB72', bg='#081208', font=('Consolas', 9), wraplength=340).pack()

    def next_device():
        state.new_qr()
        refresh(force=True)

    tk.Button(root, text='PAIR ANOTHER DEVICE', command=next_device,
              fg='#081208', bg='#A1FF93', font=('Consolas', 10, 'bold')).pack(pady=12)

    current = {'payload':None, 'count':state.paired_count}
    def refresh(force=False):
        if state.paired_count != current['count']:
            current['count'] = state.paired_count
            current['payload'] = None
            qr_label.configure(image='', text='✓ PAIRED', fg='#081208',
                               font=('Consolas', 24, 'bold'), width=18, height=7)
            status.set('Phone paired. Overlay will minimise; restore it to pair another device.')
            root.after(3500, root.iconify)
        elif current['count'] == state.paired_count:
            payload = state.qr_invite(advertise_host, port)
            if payload and (force or payload != current['payload']):
                q = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=6, border=4)
                q.add_data(payload)
                q.make(fit=True)
                im = q.make_image(fill_color='#081208', back_color='white').convert('RGB')
                im.thumbnail((285,285), Image.Resampling.NEAREST)
                rendered = ImageTk.PhotoImage(im)
                qr_label.configure(image=rendered, text='', width=285, height=285)
                qr_label.image = rendered
                current['payload'] = payload
                status.set(f'One-time QR • expires in 2 min • {advertise_host}:{port}')
        root.after(1000, refresh)

    refresh()
    root.mainloop()
